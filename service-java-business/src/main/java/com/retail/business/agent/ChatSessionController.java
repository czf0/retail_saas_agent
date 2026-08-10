package com.retail.business.agent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.ChatSessionCreateReq;
import com.retail.business.dto.req.ChatSessionRenameReq;
import com.retail.business.dto.resp.ChatMessageResp;
import com.retail.business.dto.resp.ChatSessionResp;
import com.retail.business.service.ChatSessionService;
import com.retail.core.enums.ErrCodeEnum;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能对话会话管理接口.
 * <p>路由前缀 /api/v1/chat.chat_session / chat_message 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离;
 * /internal/sessions 端点为服务间调用(Python 回源),通过 X-Tenant-ID / X-Store-ID 请求头显式传递身份,绕过登录态.
 * <p>权限校验基于 @SaCheckPermission("business:chat:*") 注解(AOP),查询需 business:chat:query,
 * 创建 / 重命名 / 删除需 business:chat:manage;对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>路由冲突说明:{sessionId} 是 String 类型(sess_uuid),与项目约束「/{id} Long 类型需加 \d+」不冲突.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    // ==================== 前端会话管理 ====================

    /** 查询当前用户的会话列表(按 updated_at desc) */
    @GetMapping("/sessions")
    @SaCheckPermission("business:chat:query")
    public R<List<ChatSessionResp>> listSessions() {
        return R.ok(chatSessionService.listSessions());
    }

    /** 创建新会话 */
    @PostMapping("/sessions")
    @SaCheckPermission("business:chat:manage")
    public R<ChatSessionResp> createSession(@RequestBody(required = false) ChatSessionCreateReq req) {
        String title = req != null ? req.getTitle() : null;
        return R.ok(chatSessionService.createSession(title));
    }

    /** 重命名会话 */
    @PatchMapping("/sessions/{sessionId}")
    @SaCheckPermission("business:chat:manage")
    public R<ChatSessionResp> renameSession(@PathVariable String sessionId,
                                            @RequestBody ChatSessionRenameReq req) {
        return R.ok(chatSessionService.renameSession(sessionId, req.getTitle()));
    }

    /** 逻辑删除会话(同时删除会话下所有消息) */
    @DeleteMapping("/sessions/{sessionId}")
    @SaCheckPermission("business:chat:manage")
    public R<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return R.ok(null);
    }

    /** 查询会话消息历史(按 created_at asc) */
    @GetMapping("/sessions/{sessionId}/messages")
    @SaCheckPermission("business:chat:query")
    public R<List<ChatMessageResp>> getMessages(@PathVariable String sessionId) {
        return R.ok(chatSessionService.getMessages(sessionId));
    }

    // ==================== Python 回源拉取(cache-aside 缓存未命中时调用)====================

    /**
     * Python 回源拉取会话消息历史.
     * <p>
     * 服务间调用,无 Sa-Token 登录态,不加 {@link SaCheckLogin}.
     * 校验请求头 X-Tenant-ID / X-Store-ID 必须存在,防止越权访问其他租户会话历史.
     * 返回最近 20 条消息(时间正序,供 Python LLM 上下文使用).
     */
    @GetMapping("/internal/sessions/{sessionId}/messages")
    public R<List<ChatMessageResp>> getMessagesForAgent(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-Store-ID", required = false) String storeIdHeader) {
        // 越权校验:X-Tenant-ID 必须存在
          if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
              return R.fail(ErrCodeEnum.TENANT_MISSING);
          }
        Long tenantId = parseLong(tenantIdHeader);
        Long storeId = parseLong(storeIdHeader);
        return R.ok(chatSessionService.getMessagesForAgent(sessionId, tenantId, storeId, limit));
    }

    /** 安全解析 Long(解析失败返回 null) */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
