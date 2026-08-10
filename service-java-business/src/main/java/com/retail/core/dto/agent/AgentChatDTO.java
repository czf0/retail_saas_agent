package com.retail.core.dto.agent;

import lombok.Data;

/**
 * Agent 对话请求 DTO (Java → Python /stream/chat 或 /chat 入参).
 * <p>触发时机: 用户在聊天会话中提问时, 由 ChatSessionService 组装并交 AgentHttpClient 透传 Python.
 * <p>解决的问题: 定义与 Python 交互的强类型请求结构, 身份上下文经 fillContext 自动填充,
 * 供 Python ContextMiddleware 加载到线程上下文.
 * <p>使用约束: 字段名必须与 Python Pydantic Model 对齐 (snake_case 键名在 body 中映射),
 * 禁止修改字段名否则 Python 端解析失败; 用户提问放在 query, 会话区分用 sessionId.
 */
@Data
public class AgentChatDTO {
    // 用户提问
    private String query;
    // 会话ID,区分上下文记忆
    private String sessionId;
    // 租户上下文
    private String tenantId;
    private String storeId;
    private String role;
    /** 角色 ID (sys_role.id 字符串, 供 Python RAG 业务过滤按角色 ID 隔离文档可见性) */
    private String roleId;
    private String traceId;
    // 调用者用户ID,从 LoginUser.userId 填充,透传给 Python 供工具层软拒绝
    private Long userId;
}
