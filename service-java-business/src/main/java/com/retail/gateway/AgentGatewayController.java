package com.retail.gateway;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.service.ChatSessionService;
import com.retail.core.client.AgentHttpClient;
import com.retail.core.dto.agent.AgentChatDTO;
import com.retail.core.dto.agent.AgentResumeDTO;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 网关控制器(三端打通:前端 → Java → Python).
 * <p>路由前缀 /api/v1/agent.chat_session / chat_message 表为多租户表,
 * tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:chat:*") 注解(AOP),
 * 一次性 / 流式对话需 business:chat:query,HITL 审批恢复需 business:chat:manage;
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>端点说明:/chat(一次性问答,不持久化),/stream/chat(SSE 流式,持久化消息),
 * /stream/resume(HITL 审批后恢复被暂停的 graph 执行).
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentGatewayController {
    private final AgentHttpClient agentHttpClient;
    private final ChatSessionService chatSessionService;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>AgentHttpClient 用于一次性对话直接转发 Python;ChatSessionService 用于流式对话编排与会话持久化.
     */
    public AgentGatewayController(AgentHttpClient agentHttpClient, ChatSessionService chatSessionService) {
        this.agentHttpClient = agentHttpClient;
        this.chatSessionService = chatSessionService;
    }

    /**
     * 普通一次性问答(不持久化到 chat_message).
     */
    @PostMapping("/chat")
    @SaCheckPermission("business:chat:query")
    public R<Object> chat(@RequestBody AgentChatDTO dto) {
        return agentHttpClient.chat(dto);
    }

    /**
     * SSE 流式对话输出.
     * <p>委托 {@link ChatSessionService#streamChat} 编排:
     * 持久化用户消息 → 转发 Python SSE(过滤工具分片)→ done 时持久化 assistant 消息.
     * 前端实时收到过滤后分片,持久化在后台异步完成.
     */
    @PostMapping("/stream/chat")
    @SaCheckPermission("business:chat:query")
    public SseEmitter streamChat(@RequestBody AgentChatDTO dto) {
        return chatSessionService.streamChat(dto);
    }

    /**
     * HITL 审批恢复 SSE 流式输出.
     * <p>委托 {@link ChatSessionService#streamResume} 编排:
     * 转发 Python /stream/resume 恢复被 interrupt() 暂停的 graph → done 时持久化 assistant 消息.
     * <p>触发时机: 前端收到 pending_approval chunk 后展示审批弹窗, 用户选择批准/拒绝后调用此接口.
     */
    @PostMapping("/stream/resume")
    @SaCheckPermission("business:chat:manage")
    public SseEmitter streamResume(@RequestBody AgentResumeDTO dto) {
        return chatSessionService.streamResume(dto);
    }
}
