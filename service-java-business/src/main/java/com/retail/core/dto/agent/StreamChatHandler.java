package com.retail.core.dto.agent;

/**
 * SSE 流式对话完成回调(AgentHttpClient → ChatSessionService).
 * <p>
 * AgentHttpClient.streamChat 在收到 Python done 分片时调用 {@link #onDone},
 * ChatSessionServiceImpl 实现此接口将 assistant 消息持久化到 chat_message 表.
 * <p>
 * 工具信息(toolsJson)由 AgentHttpClient 从 tool_call/tool_result 分片和 done.meta.used_tools
 * 中提取并序列化为 JSON 字符串,仅审计存储不展示前端.
 * <p>
 * HITL: 收到 pending_approval 分片时调用 {@link #onPendingApproval},
 * ChatSessionServiceImpl 持久化为 intent='pending_approval' 的 assistant 消息,
 * content 存储工具信息 JSON (含 tool/args/description), 供前端刷新页面后恢复审批状态.
 */
public interface StreamChatHandler {

    /**
     * @param sessionId   会话 id(done 分片携带,可能由 Python 新建)
     * @param content     完整回答内容(done 分片的 content,若为空则用累加的 token 分片)
     * @param intent      意图标签(done.meta.intent,可能为 null)
     * @param toolsJson   工具调用详情 JSON(done.meta.used_tools 序列化,可能为 null)
     * @param tokensUsed  token 消耗(done.meta.tokens_used,可能为 null)
     */
    void onDone(String sessionId, String content, String intent, String toolsJson, Integer tokensUsed);

    /**
     * HITL 审批请求回调: 持久化 pending_approval 消息供前端刷新恢复.
     * <p>
     * 默认空实现 (向后兼容); ChatSessionServiceImpl 覆写为持久化 assistant 消息:
     * intent='pending_approval', content=工具信息 JSON.
     *
     * @param sessionId   会话 id
     * @param toolInfoJson 工具信息 JSON (含 tool/args/description, 供前端解析恢复审批卡片)
     */
    default void onPendingApproval(String sessionId, String toolInfoJson) {
        // 默认空实现, 由 ChatSessionServiceImpl 覆写
    }
}
