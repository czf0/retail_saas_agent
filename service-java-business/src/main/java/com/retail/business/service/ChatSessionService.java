package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.resp.ChatMessageResp;
import com.retail.business.dto.resp.ChatSessionResp;
import com.retail.business.entity.ChatSession;
import com.retail.core.dto.agent.AgentChatDTO;
import com.retail.core.dto.agent.AgentResumeDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能对话会话服务(三端打通:Java MySQL 为权威数据源 SSOT).
 * <p>
 * 负责会话 CRUD + 消息持久化 + 流式编排 + Python 回源查询.
 * <ul>
 *   <li>前端会话管理:listSessions / createSession / renameSession / deleteSession / getMessages</li>
 *   <li>流式编排:streamChat(持久化用户消息 → 转发 Python SSE → 持久化 assistant 消息)</li>
 *   <li>Python 回源:getMessagesForAgent(cache-aside 缓存未命中时回调)</li>
 * </ul>
 */
public interface ChatSessionService extends IService<ChatSession> {

    // ---- 前端会话管理 ----

    /** 查询当前用户的会话列表(按 tenant + user 过滤,updated_at desc) */
    List<ChatSessionResp> listSessions();

    /** 创建新会话(生成 sess_<uuid>,填充 userId/tenantId/storeId) */
    ChatSessionResp createSession(String title);

    /** 重命名会话 */
    ChatSessionResp renameSession(String sessionId, String title);

    /** 逻辑删除会话(同时逻辑删除会话下所有消息) */
    void deleteSession(String sessionId);

    /** 查询会话消息历史(按 created_at asc) */
    List<ChatMessageResp> getMessages(String sessionId);

    // ---- 流式编排 ----

    /**
     * 流式对话编排:持久化用户消息 → 转发 Python SSE → done 时持久化 assistant 消息.
     * <p>
     * 流程:
     * <ol>
     *   <li>sessionId 为空 → createSession() 新建,回填到 dto.sessionId</li>
     *   <li>appendUserMessage(sessionId, query) 持久化用户消息</li>
     *   <li>agentHttpClient.streamChat(dto, onDone 回调) 转发 Python SSE</li>
     *   <li>done 分片回调 → appendAssistantMessage(...) 持久化 assistant 消息</li>
     * </ol>
     * 前端实时收到过滤后分片,持久化在后台完成.
     */
    SseEmitter streamChat(AgentChatDTO dto);

    /**
     * HITL 审批恢复流式编排: 用户审批后转发 Python /stream/resume, done 时持久化 assistant 消息.
     * <p>
     * 流程:
     * <ol>
     *   <li>agentHttpClient.resumeStream(dto, onDone 回调) 转发 Python SSE 恢复请求</li>
     *   <li>done 分片回调 → appendAssistantMessage(...) 持久化 assistant 消息</li>
     * </ol>
     * 与 streamChat 的区别: 不持久化用户消息 (query 已在首次请求时持久化),
     * 仅转发恢复请求 + done 时持久化 assistant 消息.
     */
    SseEmitter streamResume(AgentResumeDTO dto);

    // ---- Python 回源拉取(cache-aside 缓存未命中时调用)----

    /**
     * Python 回源专用:按 session_id + tenant_id + store_id 查询消息历史.
     * <p>
     * 不做用户过滤(Python 透传的是会话级 tenant/store 上下文,非用户态),
     * 但做 tenant/store 校验防止越权. 返回最近 20 条(避免上下文过长).
     */
    List<ChatMessageResp> getMessagesForAgent(String sessionId, Long tenantId, Long storeId, Integer limit);
}
