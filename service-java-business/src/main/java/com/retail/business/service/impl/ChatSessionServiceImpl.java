package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.retail.business.convert.ChatMessageConvert;
import com.retail.business.convert.ChatSessionConvert;
import com.retail.business.dto.resp.ChatMessageResp;
import com.retail.business.dto.resp.ChatSessionResp;
import com.retail.business.entity.ChatMessage;
import com.retail.business.entity.ChatSession;
import com.retail.business.mapper.ChatMessageMapper;
import com.retail.business.mapper.ChatSessionMapper;
import com.retail.business.service.ChatSessionService;
import com.retail.business.service.LongMemoryService;
import com.retail.core.client.AgentHttpClient;
import com.retail.core.context.AuditUserContext;
import com.retail.core.dto.agent.AgentChatDTO;
import com.retail.core.dto.agent.AgentResumeDTO;
import com.retail.core.dto.agent.StreamChatHandler;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 智能对话会话服务实现(三端打通:Java MySQL 为权威数据源 SSOT).
 * <p>
 * 职责:
 * <ul>
 *   <li>会话 CRUD + 消息持久化(chat_session / chat_message)</li>
 *   <li>流式编排:streamChat 持久化用户消息 → 转发 Python SSE → done 时持久化 assistant 消息</li>
 *   <li>Python 回源:getMessagesForAgent 供 Python cache-aside 缓存未命中时拉取历史</li>
 * </ul>
 * 继承 {@link BaseServiceImpl} 确保逻辑删除时 deleteAt / deleteBy 被填充.
 * <p>
 * 租户隔离:chat_session / chat_message 不在 ignore-tables 中,MyBatis-Plus 拦截器自动注入 tenant_id;
 * 门店隔离:chat_session / chat_message 在 store.tables 白名单中,StoreLineHandler 自动注入 store_id;
 * 用户隔离:listSessions 手动追加 user_id 过滤(参照 SysUser/SysRole/SysStore 的 effectiveTenantId 模式).
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends BaseServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final ChatMessageMapper chatMessageMapper;
    private final AgentHttpClient agentHttpClient;
    /** 长期记忆服务 (stream 结束后异步触发抽取) */
    private final LongMemoryService longMemoryService;
    /** 会话实体→Resp 转换器(MapStruct 生成,替代原手写 toSessionResp) */
    private final ChatSessionConvert chatSessionConvert;
    /** 消息实体→Resp 转换器(MapStruct 生成,替代原手写 toMessageResp) */
    private final ChatMessageConvert chatMessageConvert;
    /** 通用异步线程池(替代 new Thread,防止并发高时线程无限膨胀) */
    private final Executor asyncExecutor;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>chatSessionConvert / chatMessageConvert 为 MapStruct 生成的 Spring Bean,
     * 替代原手写 toSessionResp / toMessageResp(含 LocalDateTime→毫秒的异常转化已移除).
     */
    public ChatSessionServiceImpl(ChatSessionMapper chatSessionMapper,
                                  ChatMessageMapper chatMessageMapper,
                                  AgentHttpClient agentHttpClient,
                                  LongMemoryService longMemoryService,
                                  ChatSessionConvert chatSessionConvert,
                                  ChatMessageConvert chatMessageConvert,
                                  @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.chatMessageMapper = chatMessageMapper;
        this.agentHttpClient = agentHttpClient;
        this.longMemoryService = longMemoryService;
        this.chatSessionConvert = chatSessionConvert;
        this.chatMessageConvert = chatMessageConvert;
        this.asyncExecutor = asyncExecutor;
    }

    // ==================== 前端会话管理 ====================

    /**
     * 查询当前用户的会话列表.
     * <p>
     * tenant_id 由拦截器自动过滤;user_id 手动追加(确保用户只看自己的会话);
     * 平台管理员切换租户时,拦截器基于 TenantContext(X-Tenant-Id 头)过滤所选租户.
     */
    @Override
    public List<ChatSessionResp> listSessions() {
        Long userId = LoginUserHolder.currentUserId();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        // 用户隔离:仅查询当前用户创建的会话
        if (userId != null) {
            wrapper.eq(ChatSession::getUserId, userId);
        }
        wrapper.orderByDesc(ChatSession::getUpdatedAt);
        return chatSessionConvert.toRespList(list(wrapper));
    }

    /**
     * 创建新会话.
     * <p>
     * 生成 sessionId = "sess_" + UUID(与 Python memory key 对齐);
     * userId / tenantId / storeId 从登录上下文填充(拦截器自动注入 tenant_id / store_id).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResp createSession(String title) {
        ChatSession session = new ChatSession();
        session.setSessionId("sess_" + UUID.randomUUID().toString().replace("-", ""));
        session.setTitle(title != null && !title.isBlank() ? title : "新对话");
        session.setUserId(LoginUserHolder.currentUserId());
        session.setMessageCount(0);
        // tenantId / storeId 由拦截器自动注入
        save(session);
        return chatSessionConvert.toResp(session);
    }

    /** 重命名会话 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResp renameSession(String sessionId, String title) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }
        session.setTitle(title);
        updateById(session);
        return chatSessionConvert.toResp(session);
    }

    /**
     * 逻辑删除会话(同时逻辑删除会话下所有消息).
     * <p>
     * 会话:调用 {@link BaseServiceImpl#removeById} 填充 deleteAt / deleteBy;
     * 消息:批量 UpdateWrapper 设置 deleted=1 + deleteAt + deleteBy.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            return;
        }
        // 逻辑删除会话(BaseServiceImpl.removeById 填充 deleteAt / deleteBy)
        removeById(session.getId());
        // 逻辑删除会话下所有消息(批量)
        String user = AuditUserContext.currentUser();
        chatMessageMapper.update(null, new UpdateWrapper<ChatMessage>()
                .eq("session_id", sessionId)
                .eq("deleted", 0)
                .setSql("deleted=1")
                .set("delete_at", LocalDateTime.now())
                .set("delete_by", user));
    }

    /** 查询会话消息历史(按 created_at asc) */
    @Override
    public List<ChatMessageResp> getMessages(String sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        wrapper.orderByAsc(ChatMessage::getCreatedAt);
        return chatMessageConvert.toRespList(chatMessageMapper.selectList(wrapper));
    }

    // ==================== 流式编排 ====================

    /**
     * 流式对话编排:持久化用户消息 → 转发 Python SSE → done 时持久化 assistant 消息.
     * <p>
     * 流程:
     * <ol>
     *   <li>sessionId 为空 → createSession() 新建,回填到 dto.sessionId</li>
     *   <li>appendUserMessage 持久化用户消息</li>
     *   <li>agentHttpClient.streamChat 转发 Python SSE,注册 onDone 回调</li>
     *   <li>done 分片回调 → appendAssistantMessage 持久化 assistant 消息</li>
     * </ol>
     * 前端实时收到过滤后分片,持久化在后台异步完成.
     */
    @Override
    public SseEmitter streamChat(AgentChatDTO dto) {
        // 1. sessionId 为空 → 新建会话
        if (dto.getSessionId() == null || dto.getSessionId().isBlank()) {
            ChatSessionResp session = createSession(null);
            dto.setSessionId(session.getSessionId());
        }
        // 2. 持久化用户消息
        appendUserMessage(dto.getSessionId(), dto.getQuery());
        // 捕获当前租户上下文(done 回调在 agent-sse-forward 异步线程中执行,
        // ThreadLocal 不跨线程继承,需手动恢复供 MyBatis-Plus 拦截器注入 tenant_id)
        String tenantId = TenantContext.getTenantId();
        String storeId = TenantContext.getStoreId();
        Long userId = dto.getUserId();
        // 3. 转发 Python SSE,注册 done 回调持久化 assistant 消息
        //    匿名类 (非 lambda): 需同时覆写 onDone + onPendingApproval (HITL 持久化)
        String sessionId0 = dto.getSessionId();
        StreamChatHandler handler = new StreamChatHandler() {
            @Override
            public void onDone(String sessionId, String content, String intent, String toolsJson, Integer tokensUsed) {
                TenantContext.setTenantId(tenantId);
                TenantContext.setStoreId(storeId);
                try {
                    appendAssistantMessage(
                            sessionId != null ? sessionId : sessionId0,
                            content, intent, toolsJson, tokensUsed);
                    // 流式结束后异步触发长期记忆抽取 (同一会话下一轮即生效)
                    triggerMemoryExtractAsync(userId, tenantId, storeId,
                            sessionId != null ? sessionId : sessionId0);
                } finally {
                    TenantContext.clear();
                }
            }
            @Override
            public void onPendingApproval(String sessionId, String toolInfoJson) {
                TenantContext.setTenantId(tenantId);
                TenantContext.setStoreId(storeId);
                try {
                    String sid = sessionId != null && !sessionId.isBlank() ? sessionId : sessionId0;
                    appendPendingApprovalMessage(sid, toolInfoJson);
                    log.info("HITL 审批消息已持久化 session={} toolInfo={}", sid, toolInfoJson);
                } catch (Exception e) {
                    // 持久化失败不中断 SSE 流: 前端仍可实时展示审批卡片, 但刷新后无法恢复
                    log.error("HITL 审批消息持久化失败 session={}", sessionId, e);
                } finally {
                    TenantContext.clear();
                }
            }
        };
        return agentHttpClient.streamChat(dto, handler);
    }

    /**
     * HITL 审批恢复流式编排: 转发 Python /stream/resume, done 时持久化 assistant 消息.
     * <p>
     * 与 streamChat 的区别:
     * <ul>
     *   <li>不持久化用户消息 (query 已在首次请求 streamChat 时持久化)</li>
     *   <li>不新建会话 (session_id 必须已存在, 由前端从 pending_approval chunk 中获取)</li>
     *   <li>done 回调同样持久化 assistant 消息 (审批后的完整回答)</li>
     * </ul>
     * 租户上下文捕获与 streamChat 一致 (done 回调在异步线程中执行, 需手动恢复).
     */
    @Override
    public SseEmitter streamResume(AgentResumeDTO dto) {
        // 捕获当前租户上下文 (done 回调在 agent-sse-resume 异步线程中执行,
        // ThreadLocal 不跨线程继承, 需手动恢复供 MyBatis-Plus 拦截器注入 tenant_id)
        String tenantId = TenantContext.getTenantId();
        String storeId = TenantContext.getStoreId();
        String sessionId0 = dto.getSessionId();
        Long userId = dto.getUserId();
        StreamChatHandler handler = new StreamChatHandler() {
            @Override
            public void onDone(String sessionId, String content, String intent, String toolsJson, Integer tokensUsed) {
                TenantContext.setTenantId(tenantId);
                TenantContext.setStoreId(storeId);
                try {
                    appendAssistantMessage(
                            sessionId != null ? sessionId : sessionId0,
                            content, intent, toolsJson, tokensUsed);
                    // HITL 审批完成后同样异步触发长期记忆抽取
                    triggerMemoryExtractAsync(userId, tenantId, storeId,
                            sessionId != null ? sessionId : sessionId0);
                } finally {
                    TenantContext.clear();
                }
            }
            // resume 流中也可能出现 pending_approval (多个破坏性工具逐一审批), 同样持久化
            @Override
            public void onPendingApproval(String sessionId, String toolInfoJson) {
                TenantContext.setTenantId(tenantId);
                TenantContext.setStoreId(storeId);
                try {
                    String sid = sessionId != null && !sessionId.isBlank() ? sessionId : sessionId0;
                    appendPendingApprovalMessage(sid, toolInfoJson);
                    log.info("HITL 审批消息已持久化 session={} toolInfo={}", sid, toolInfoJson);
                } catch (Exception e) {
                    // 持久化失败不中断 SSE 流: 前端仍可实时展示审批卡片, 但刷新后无法恢复
                    log.error("HITL 审批消息持久化失败 session={}", sessionId, e);
                } finally {
                    TenantContext.clear();
                }
            }
        };
        return agentHttpClient.resumeStream(dto, handler);
    }

    // ==================== Python 回源拉取(cache-aside)====================

    /**
     * Python 回源专用:按 session_id + tenant_id + store_id 查询消息历史.
     * <p>
     * 不做用户过滤(Python 透传的是会话级 tenant/store 上下文,非用户态),
     * 显式按 tenant_id + store_id 过滤防止越权. 返回最近 {@value #AGENT_FETCH_LIMIT} 条.
     */
    @Override
    public List<ChatMessageResp> getMessagesForAgent(String sessionId, Long tenantId, Long storeId, Integer limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        if (tenantId != null) {
            wrapper.eq(ChatMessage::getTenantId, tenantId);
        }
        if (storeId != null) {
            wrapper.eq(ChatMessage::getStoreId, storeId);
        }
        wrapper.orderByDesc(ChatMessage::getCreatedAt);
        wrapper.last("LIMIT " + limit);
        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        // 反转为时间正序(供 Python LLM 上下文使用)
        java.util.Collections.reverse(messages);
        return chatMessageConvert.toRespList(messages);
    }

    // ==================== 内部持久化方法 ====================

    /**
     * 持久化用户消息 + 更新会话计数/预览.
     * <p>
     * 在请求线程执行(streamChat 调用前),可参与事务.
     */
    @Transactional(rollbackFor = Exception.class)
    public void appendUserMessage(String sessionId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setContent(content);
        chatMessageMapper.insert(message);
        updateSessionStats(sessionId, content);
    }

    /**
     * 持久化 assistant 消息 + 更新会话计数/预览.
     * <p>
     * 在 AgentHttpClient 的 SSE 读取线程中异步执行(done 分片回调),
     * 无请求线程事务上下文,MyBatis-Plus 操作各自自动提交.
     */
    public void appendAssistantMessage(String sessionId, String content, String intent,
                                       String toolsJson, Integer tokensUsed) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(content);
        message.setIntent(intent);
        message.setToolsJson(toolsJson);
        message.setTokensUsed(tokensUsed);
        chatMessageMapper.insert(message);
        updateSessionStats(sessionId, content);
    }

    /**
     * 持久化 HITL 审批请求消息.
     * <p>
     * intent='pending_approval' 标记此消息为审批请求 (前端据此恢复审批卡片);
     * content=工具信息 JSON (含 tool/args/description, 供前端解析展示);
     * 与 appendAssistantMessage 的区别: 不更新 lastMessagePreview (审批请求不是最终回答).
     * <p>
     * 刷新页面恢复: 前端加载消息时检测 intent='pending_approval', 解析 content 为 JSON,
     * 若该消息为最后一条 (审批未完成) 则展示审批卡片, 否则视为已解决.
     */
    public void appendPendingApprovalMessage(String sessionId, String toolInfoJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(toolInfoJson);
        message.setIntent("pending_approval");
        chatMessageMapper.insert(message);
    }

    /**
     * 异步触发长期记忆抽取 (stream 结束后, 不阻塞 SSE).
     * <p>
     * done 回调运行在 AgentHttpClient 的 SSE 读取线程, 若在其中同步执行抽取 (调 Python + 写 DB)
     * 会延迟 emitter.complete(). 故再开独立线程执行抽取, 新线程内恢复 TenantContext 供
     * MyBatis-Plus 拦截器注入 tenant_id (ThreadLocal 不跨线程继承).
     * <p>
     * 抽取全链路有独立兜底 (LongMemoryService.triggerExtract 内部 try-catch), 失败不影响主流程.
     */
    private void triggerMemoryExtractAsync(Long userId, String tenantId, String storeId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        Long tenantLong = parseTenantId(tenantId);
        if (tenantLong == null) {
            return;
        }
        asyncExecutor.execute(() -> {
            TenantContext.setTenantId(tenantId);
            TenantContext.setStoreId(storeId);
            try {
                longMemoryService.triggerExtract(userId, tenantLong, sessionId);
            } catch (Exception e) {
                log.warn("memory_trigger_extract_async_error session={}: {}", sessionId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        });
    }

    /** 安全解析租户 ID (Long), 解析失败返回 null */
    private Long parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 更新会话的 messageCount + lastMessagePreview + updatedAt.
     */
    private void updateSessionStats(String sessionId, String latestContent) {
        ChatSession session = getBySessionId(sessionId);
        if (session == null) {
            return;
        }
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        // 预览截断 200 字符
        String preview = latestContent != null && latestContent.length() > 200
                ? latestContent.substring(0, 200) : latestContent;
        session.setLastMessagePreview(preview);
        session.setUpdatedAt(LocalDateTime.now());
        updateById(session);
    }

    // ==================== 辅助方法 ====================

    /** 按 sessionId 查询未删除的会话实体 */
    private ChatSession getBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return getOne(wrapper);
    }
}
