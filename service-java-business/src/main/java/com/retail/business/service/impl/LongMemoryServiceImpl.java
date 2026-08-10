package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.retail.business.dto.resp.MemoryExtractResp;
import com.retail.business.dto.resp.MemoryOperationResp;
import com.retail.business.dto.resp.MemoryResp;
import com.retail.business.entity.ChatMessage;
import com.retail.business.entity.LongMemory;
import com.retail.business.enums.MemoryCategory;
import com.retail.business.mapper.ChatMessageMapper;
import com.retail.business.mapper.LongMemoryMapper;
import com.retail.business.service.LongMemoryService;
import com.retail.core.client.MemoryAgentClient;
import com.retail.core.context.AuditUserContext;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆服务实现 (Java 存储 SSOT + 触发时机控制).
 * <p>
 * 核心流程 (对齐《长期记忆系统整改方案》5.2 / 5.4):
 * <ul>
 *   <li>抽取: Redis 增量游标 → 拉该会话 id>cursor 的新消息 → MemoryAgentClient 调 Python 抽取
 *       → {@link #applyOperations} 落库 (同分类槽位覆盖) → 收到 200+合法 JSON 才推进游标;</li>
 *   <li>巩固: 槽位溢出检测 (OTHER 超 MEMORY_OTHER_SLOT_MAX 或核心分类重复) → Python 巩固 → 落库;</li>
 *   <li>槽位覆盖: category 0-6 每 (tenant,user) 固定 1 条 (add 遇同类 → update 覆盖);
 *       OTHER(100) 允许 {@code otherSlotMax} 条, 超限 update 覆盖 importance 最低者.</li>
 * </ul>
 * 租户隔离: tenant_id 由 MyBatis-Plus 拦截器自动注入 (本服务在 SSE 线程执行, 调用方已恢复 TenantContext);
 * 用户作用域: user_id 手动过滤/写入.
 */
@Slf4j
@Service
public class LongMemoryServiceImpl extends BaseServiceImpl<LongMemoryMapper, LongMemory>
        implements LongMemoryService {

    private final LongMemoryMapper longMemoryMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MemoryAgentClient memoryAgentClient;
    private final StringRedisTemplate redisTemplate;

    /** OTHER 分类槽位上限 (与 Python MEMORY_OTHER_SLOT_MAX 对齐) */
    @Value("${agent.memory.other-slot-max:3}")
    private int otherSlotMax;

    /** 置信度入库门槛 (双保险, Python 侧已按同阈值过滤) */
    @Value("${agent.memory.confidence-threshold:0.7}")
    private BigDecimal confidenceThreshold;

    /** 单次抽取最多拉取的新消息条数 (防一次抽取过多) */
    private static final int EXTRACT_MSG_LIMIT = 50;

    /** 增量游标 Redis key 前缀 */
    private static final String CURSOR_PREFIX = "memory:extract_cursor:";

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 LongMemoryMapper)由 {@link com.retail.core.service.BaseServiceImpl} 基于泛型自动注入,
     * 此处再显式声明一份供 applyOperations 等方法直接调用(MyBatis-Plus 批量写入不走 baseMapper).
     * <p>chatMessageMapper 用于 extract 拉取候选新消息;memoryAgentClient 调 Python 抽取/巩固;
     * redisTemplate 维护增量游标(extract_cursor),避免重复抽取同一条消息.
     */
    public LongMemoryServiceImpl(LongMemoryMapper longMemoryMapper,
                                 ChatMessageMapper chatMessageMapper,
                                 MemoryAgentClient memoryAgentClient,
                                 StringRedisTemplate redisTemplate) {
        this.longMemoryMapper = longMemoryMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.memoryAgentClient = memoryAgentClient;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 读取候选 ====================

    @Override
    public List<MemoryResp> listCandidates(Long tenantId, Long userId, Integer limit) {
        LambdaQueryWrapper<LongMemory> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(LongMemory::getUserId, userId);
        }
        // 重要性 + 最近访问时间降序 (重排加权的基础序); deleted=0 由全局逻辑删除自动过滤
        wrapper.orderByDesc(LongMemory::getImportance)
                .orderByDesc(LongMemory::getLastAccessedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return longMemoryMapper.selectList(wrapper).stream()
                .map(this::toResp)
                .collect(Collectors.toList());
    }

    // ==================== 用户删除 ====================

    @Override
    public void deleteMemory(Long id) {
        if (id == null) {
            return;
        }
        // 软删除: 设置 deleted=1 + delete_at + delete_by.
        // deleted 为逻辑删除字段 (@TableLogic 由全局 logic-delete-field 标记), 常规 .set("deleted",1) 会被
        // LogicSqlInjector 从 SET 子句剥离, 故用 .setSql("deleted=1") 拼接原始 SQL (与 BaseServiceImpl 一致).
        String user = AuditUserContext.currentUser();
        longMemoryMapper.update(null, new LambdaUpdateWrapper<LongMemory>()
                .eq(LongMemory::getId, id)
                .eq(LongMemory::getDeleted, 0)
                .setSql("deleted=1")
                .set(LongMemory::getDeleteAt, LocalDateTime.now())
                .set(LongMemory::getDeleteBy, user));
        log.info("memory_deleted id={} by={}", id, user);
    }

    // ==================== 抽取触发 ====================

    @Override
    public void triggerExtract(Long userId, Long tenantId, String sessionId) {
        if (userId == null || tenantId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Long cursor = getCursor(tenantId, userId);
            // 拉取该会话中 id > cursor 的新消息 (时间正序)
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessage::getSessionId, sessionId)
                    .gt(ChatMessage::getId, cursor == null ? 0L : cursor)
                    .orderByAsc(ChatMessage::getId)
                    .last("LIMIT " + EXTRACT_MSG_LIMIT);
            List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
            if (messages.isEmpty()) {
                return;
            }
            // 构建增量对话 (role/content)
            List<Map<String, String>> conversation = new ArrayList<>();
            for (ChatMessage m : messages) {
                Map<String, String> item = new HashMap<>();
                item.put("role", m.getRole() == null ? "" : m.getRole());
                item.put("content", m.getContent() == null ? "" : m.getContent());
                conversation.add(item);
            }
            Long toMsgId = messages.get(messages.size() - 1).getId();

            // 调 Python 抽取
            MemoryExtractResp resp = memoryAgentClient.extract(
                    tenantId, userId, sessionId, cursor, toMsgId, conversation);
            if (resp == null) {
                // HTTP 非 200 或 JSON 不合法: 不推进游标, 下次 stream 结束重抽同一批
                log.warn("memory_extract_skip_cursor tenant={} user={} session={} (python不可用/解析失败)",
                        tenantId, userId, sessionId);
                return;
            }
            // 落库 (同分类槽位覆盖)
            applyOperations(userId, tenantId, resp.getOperations(), toMsgId);
            // 收到 200 + 合法 JSON (哪怕空操作) → 推进游标
            advanceCursor(tenantId, userId, toMsgId);
            log.info("memory_extract_done tenant={} user={} session={} cursor={}->{} ops={}",
                    tenantId, userId, sessionId, cursor, toMsgId,
                    resp.getOperations() == null ? 0 : resp.getOperations().size());

            // 槽位溢出安全网: 落库后检测, 超限触发巩固
            triggerConsolidateIfNeeded(userId, tenantId);
        } catch (Exception e) {
            // 全链路兜底: 抽取失败不影响主流程
            log.error("memory_extract_error tenant={} user={} session={}: {}",
                    tenantId, userId, sessionId, e.getMessage(), e);
        }
    }

    // ==================== 巩固触发 ====================

    @Override
    public void triggerConsolidate(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        try {
            List<LongMemory> all = listAll(userId, tenantId);
            if (all.isEmpty()) {
                return;
            }
            List<Map<String, Object>> memories = toSnapshot(all);
            MemoryExtractResp resp = memoryAgentClient.consolidate(tenantId, userId, memories);
            if (resp == null) {
                log.warn("memory_consolidate_skip tenant={} user={} (python不可用/解析失败)", tenantId, userId);
                return;
            }
            applyOperations(userId, tenantId, resp.getOperations(), null);
            log.info("memory_consolidate_done tenant={} user={} ops={}",
                    tenantId, userId,
                    resp.getOperations() == null ? 0 : resp.getOperations().size());
        } catch (Exception e) {
            log.error("memory_consolidate_error tenant={} user={}: {}",
                    tenantId, userId, e.getMessage(), e);
        }
    }

    // ==================== 落库 (同分类槽位覆盖) ====================

    /**
     * 应用 Python 返回的操作到 MySQL (同分类槽位覆盖判定在 Java 侧).
     */
    private void applyOperations(Long userId, Long tenantId, List<MemoryOperationResp> ops, Long sourceMsgId) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        for (MemoryOperationResp op : ops) {
            if (op.getOp() == null) {
                continue;
            }
            switch (op.getOp()) {
                case "add" -> applyAdd(userId, tenantId, op, sourceMsgId);
                case "update" -> applyUpdate(userId, tenantId, op, sourceMsgId);
                case "delete" -> applyDelete(userId, tenantId, op);
                default -> log.warn("memory_op_unknown op={}", op.getOp());
            }
        }
    }

    /** add: 核心分类同类覆盖 / OTHER 超限覆盖 importance 最低者 / 否则新增 */
    private void applyAdd(Long userId, Long tenantId, MemoryOperationResp op, Long sourceMsgId) {
        // 置信度门槛 (双保险)
        if (op.getConfidence() != null && op.getConfidence().compareTo(confidenceThreshold) < 0) {
            log.warn("memory_add_low_confidence tenant={} user={} cat={} conf={} (低于阈值, 丢弃)",
                    tenantId, userId, op.getCategory(), op.getConfidence());
            return;
        }
        MemoryCategory cat = MemoryCategory.fromCode(op.getCategory());
        if (cat.isCore()) {
            // 核心分类: 每类固定 1 条, 已有则覆盖, 否则新增
            LongMemory existing = findOneByCategory(userId, cat);
            if (existing != null) {
                updateMemory(existing, op, sourceMsgId);
            } else {
                insertMemory(userId, op, sourceMsgId);
            }
            return;
        }
        // OTHER: 超限覆盖 importance 最低者, 否则新增
        long otherCount = countByCategory(userId, cat);
        if (otherCount >= otherSlotMax) {
            LongMemory lowest = findLowestImportance(userId, cat);
            if (lowest != null) {
                updateMemory(lowest, op, sourceMsgId);
            } else {
                insertMemory(userId, op, sourceMsgId);
            }
        } else {
            insertMemory(userId, op, sourceMsgId);
        }
    }

    /** update: 按 target_id 或同分类定位并覆盖 */
    private void applyUpdate(Long userId, Long tenantId, MemoryOperationResp op, Long sourceMsgId) {
        LongMemory target;
        if (op.getTargetId() != null) {
            target = longMemoryMapper.selectById(op.getTargetId());
        } else {
            target = findOneByCategory(userId, MemoryCategory.fromCode(op.getCategory()));
        }
        if (target != null) {
            updateMemory(target, op, sourceMsgId);
        } else {
            log.warn("memory_update_no_target tenant={} user={} cat={} targetId={}",
                    tenantId, userId, op.getCategory(), op.getTargetId());
        }
    }

    /** delete: 按 target_id 或同分类软删除 */
    private void applyDelete(Long userId, Long tenantId, MemoryOperationResp op) {
        if (op.getTargetId() != null) {
            deleteMemory(op.getTargetId());
            return;
        }
        LongMemory target = findOneByCategory(userId, MemoryCategory.fromCode(op.getCategory()));
        if (target != null) {
            deleteMemory(target.getId());
        }
    }

    /** 新增一条记忆 */
    private void insertMemory(Long userId, MemoryOperationResp op, Long sourceMsgId) {
        LongMemory m = new LongMemory();
        m.setUserId(userId);
        m.setMemoryType("preference");
        m.setCategory(MemoryCategory.fromCode(op.getCategory()));
        m.setContent(op.getContent());
        m.setConfidence(op.getConfidence() == null ? BigDecimal.ZERO : op.getConfidence());
        m.setImportance(op.getImportance() == null ? 3 : op.getImportance());
        m.setAccessCount(0);
        m.setSourceMsgId(sourceMsgId);
        m.setDeleted(0);
        // tenant_id 由拦截器自动注入
        longMemoryMapper.insert(m);
    }

    /** 覆盖已有记忆 (保留 id/tenant/user/category, 更新内容与置信度, 刷新 source_msg_id) */
    private void updateMemory(LongMemory target, MemoryOperationResp op, Long sourceMsgId) {
        target.setContent(op.getContent());
        target.setConfidence(op.getConfidence() == null ? target.getConfidence() : op.getConfidence());
        if (op.getImportance() != null) {
            target.setImportance(op.getImportance());
        }
        if (sourceMsgId != null) {
            target.setSourceMsgId(sourceMsgId);
        }
        target.setUpdatedAt(LocalDateTime.now());
        target.setUpdateBy(AuditUserContext.currentUser());
        longMemoryMapper.updateById(target);
    }

    // ==================== 槽位溢出检测 + 巩固 ====================

    /** 落库后检测槽位溢出, 触发 Python 巩固 (OTHER 超限 / 核心分类重复) */
    private void triggerConsolidateIfNeeded(Long userId, Long tenantId) {
        try {
            List<LongMemory> all = listAll(userId, tenantId);
            if (all.isEmpty()) {
                return;
            }
            Map<MemoryCategory, Long> counts = all.stream()
                    .collect(Collectors.groupingBy(LongMemory::getCategory, Collectors.counting()));
            boolean overflow = counts.entrySet().stream().anyMatch(e ->
                    (Boolean.TRUE.equals(e.getKey().isCore()) && e.getValue() > 1)
                            || (e.getValue() > otherSlotMax));
            if (!overflow) {
                return;
            }
            log.info("memory_slot_overflow tenant={} user={} counts={}, 触发巩固",
                    tenantId, userId, counts);
            triggerConsolidate(userId, tenantId);
        } catch (Exception e) {
            log.warn("memory_consolidate_check_error tenant={} user={}: {}",
                    tenantId, userId, e.getMessage());
        }
    }

    // ==================== 查询辅助 ====================

    /** 查询当前用户全部未删除记忆 */
    private List<LongMemory> listAll(Long userId, Long tenantId) {
        return list(new LambdaQueryWrapper<LongMemory>()
                .eq(LongMemory::getUserId, userId));
    }

    /** 按 (tenant[拦截器], user, category) 查询单条未删除记忆 */
    private LongMemory findOneByCategory(Long userId, MemoryCategory cat) {
        return getOne(new LambdaQueryWrapper<LongMemory>()
                .eq(LongMemory::getUserId, userId)
                .eq(LongMemory::getCategory, cat)
                .last("LIMIT 1"));
    }

    /** 按 (tenant[拦截器], user, category) 统计条数 */
    private long countByCategory(Long userId, MemoryCategory cat) {
        return count(new LambdaQueryWrapper<LongMemory>()
                .eq(LongMemory::getUserId, userId)
                .eq(LongMemory::getCategory, cat));
    }

    /** 查询 OTHER 分类中 importance 最低的一条 (超限覆盖目标) */
    private LongMemory findLowestImportance(Long userId, MemoryCategory cat) {
        return getOne(new LambdaQueryWrapper<LongMemory>()
                .eq(LongMemory::getUserId, userId)
                .eq(LongMemory::getCategory, cat)
                .orderByAsc(LongMemory::getImportance)
                .last("LIMIT 1"));
    }

    /** 记忆快照 (供 Python consolidate 入参) */
    private List<Map<String, Object>> toSnapshot(List<LongMemory> memories) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (LongMemory m : memories) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("memory_type", m.getMemoryType());
            item.put("category", m.getCategory() == null ? 100 : m.getCategory().getCode());
            item.put("content", m.getContent());
            item.put("confidence", m.getConfidence());
            item.put("importance", m.getImportance());
            item.put("access_count", m.getAccessCount());
            item.put("last_accessed_at", m.getLastAccessedAt());
            snapshots.add(item);
        }
        return snapshots;
    }

    /** 实体 → Resp */
    private MemoryResp toResp(LongMemory m) {
        MemoryResp resp = new MemoryResp();
        resp.setId(m.getId());
        resp.setMemoryType(m.getMemoryType());
        resp.setCategory(m.getCategory());
        resp.setContent(m.getContent());
        resp.setConfidence(m.getConfidence());
        resp.setImportance(m.getImportance());
        resp.setAccessCount(m.getAccessCount());
        resp.setLastAccessedAt(m.getLastAccessedAt());
        return resp;
    }

    // ==================== 增量游标 (Redis) ====================

    private String cursorKey(Long tenantId, Long userId) {
        return CURSOR_PREFIX + tenantId + ":" + userId;
    }

    private Long getCursor(Long tenantId, Long userId) {
        String val = redisTemplate.opsForValue().get(cursorKey(tenantId, userId));
        if (val == null) {
            return null;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void advanceCursor(Long tenantId, Long userId, Long toMsgId) {
        redisTemplate.opsForValue().set(cursorKey(tenantId, userId), String.valueOf(toMsgId));
    }
}
