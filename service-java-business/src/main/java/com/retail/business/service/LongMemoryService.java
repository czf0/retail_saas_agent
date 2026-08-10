package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.resp.MemoryResp;
import com.retail.business.entity.LongMemory;

import java.util.List;

/**
 * 长期记忆服务 (Java 存储 SSOT + 触发时机控制).
 * <p>
 * 职责 (对齐《长期记忆系统整改方案》5.2):
 * <ul>
 *   <li>读取候选: 供 Python Reader 拉取当前用户非删除记忆 (GET /list);</li>
 *   <li>抽取触发: 每次 chat stream 结束后异步触发, 按增量游标拉消息 → 调 Python 抽取 → 落库;</li>
 *   <li>巩固触发: 分类槽位溢出时 (OTHER 超上限 / 核心分类重复) 调 Python 巩固 → 落库;</li>
 *   <li>用户删除: 软删除 (DELETE /{id}).</li>
 * </ul>
 * 同分类槽位覆盖规则 (Java 落库侧判定):
 *   category 0-6 每 (tenant,user) 固定 1 条; OTHER(100) 允许 MEMORY_OTHER_SLOT_MAX 条, 超限覆盖 importance 最低者.
 */
public interface LongMemoryService extends IService<LongMemory> {

    /**
     * 读取当前用户候选记忆 (供 Python Reader 拉取, 默认只返回非 deleted, 按 importance+recency 排序).
     *
     * @param tenantId 租户 id (由拦截器注入, 此处用于显式查询)
     * @param userId   用户 id
     * @param limit    返回条数上限 (null/<=0 不限制)
     * @return 候选记忆列表
     */
    List<MemoryResp> listCandidates(Long tenantId, Long userId, Integer limit);

    /**
     * 用户主动删除记忆 (软删除).
     *
     * @param id 记忆主键
     */
    void deleteMemory(Long id);

    /**
     * 触发抽取 (每次 chat stream 结束后异步调用).
     * <p>
     * 按增量游标 (Redis, 每 (tenant,user) 一条) 拉取该会话 id > cursor 的新消息,
     * 调 Python /memory/extract → 落库 (同分类槽位覆盖) → 收到 HTTP 200 + 合法 JSON 才推进游标.
     * 全链路异常兜底, 不抛出 (不影响主流程/SSE).
     *
     * @param userId    用户 id
     * @param tenantId  租户 id
     * @param sessionId 会话 id
     */
    void triggerExtract(Long userId, Long tenantId, String sessionId);

    /**
     * 触发巩固 (分类槽位溢出时调用).
     * <p>
     * 拉取当前用户记忆快照 → 调 Python /memory/consolidate → 落库 (合并/去重/衰减).
     *
     * @param userId   用户 id
     * @param tenantId 租户 id
     */
    void triggerConsolidate(Long userId, Long tenantId);
}