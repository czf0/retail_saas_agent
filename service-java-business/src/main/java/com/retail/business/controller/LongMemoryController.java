package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.resp.MemoryResp;
import com.retail.business.service.LongMemoryService;
import com.retail.core.enums.ErrCodeEnum;
import com.retail.core.result.R;
import com.retail.core.security.LoginUserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆接口.
 * <p>路由前缀 /api/v1/agent/memory.long_memory 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离;
 * /list 端点为服务间调用(Python Reader),通过 X-Tenant-ID / X-User-ID 请求头显式传递身份,绕过登录态.
 * <p>权限校验基于 @SaCheckPermission("business:chat:manage") 注解(AOP),删除 / 抽取 / 巩固需 chat 域管理权限;
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>端点说明:GET /list(Python Reader 拉取候选),DELETE /{id}(用户主动删除),
 * POST /extract(事件驱动抽取),POST /consolidate(手动触发巩固).
 */
@RestController
@RequestMapping("/api/v1/agent/memory")
public class LongMemoryController {

    private final LongMemoryService longMemoryService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public LongMemoryController(LongMemoryService longMemoryService) {
        this.longMemoryService = longMemoryService;
    }

    /**
     * 读取候选记忆 (Python Reader 调用).
     * <p>
     * 服务间调用, 无 Sa-Token 登录态, 不加 {@link SaCheckLogin}.
     * 从请求头 X-Tenant-ID / X-User-ID 读取身份 (与 Python _build_headers 对齐), 校验租户存在防止越权.
     * 默认只返回 deleted=0 的非删除记忆 (供 Python 读取注入).
     */
    @GetMapping("/list")
    public R<List<MemoryResp>> list(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestHeader(value = "X-User-ID", required = false) String userHeader,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "false") Boolean includeDeleted) {
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            return R.fail(ErrCodeEnum.TENANT_MISSING);
        }
        Long tenantId = parseLong(tenantIdHeader);
        Long userId = parseLong(userHeader);
        // v1 只返回非删除记忆 (Python 读取不需要 deleted), include_deleted 参数暂不用于恢复删除项
        return R.ok(longMemoryService.listCandidates(tenantId, userId, limit));
    }

    /**
     * 用户主动删除记忆 (软删除).
     * <p>按当前用户租户隔离 (拦截器注入 tenant_id), 需 chat 域权限.
     */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("business:chat:manage")
    public R<Void> delete(@PathVariable Long id) {
        longMemoryService.deleteMemory(id);
        return R.ok(null);
    }

    /**
     * 事件驱动抽取 (v2 可选): 前端"记住这个偏好"触发.
     * <p>
     * 当前复用流式结束后的增量抽取逻辑 (按当前用户增量游标), 保证同一用户偏好立即生效.
     * 请求体可选: {sessionId} 指定会话, 缺省用当前用户最近会话.
     */
    @PostMapping("/extract")
    @SaCheckPermission("business:chat:manage")
    public R<Void> extract(@RequestBody(required = false) Map<String, Object> body) {
        Long userId = LoginUserHolder.currentUserId();
        Long tenantId = LoginUserHolder.currentTenantId();
        String sessionId = body != null && body.get("sessionId") != null
                ? String.valueOf(body.get("sessionId")) : null;
        longMemoryService.triggerExtract(userId, tenantId, sessionId);
        return R.ok(null);
    }

    /**
     * 手动触发巩固 (槽位溢出/运维触发).
     * <p>按当前用户触发 Python 巩固并落库, 需 chat 域权限.
     */
    @PostMapping("/consolidate")
    @SaCheckPermission("business:chat:manage")
    public R<Void> consolidate() {
        Long userId = LoginUserHolder.currentUserId();
        Long tenantId = LoginUserHolder.currentTenantId();
        longMemoryService.triggerConsolidate(userId, tenantId);
        return R.ok(null);
    }

    /** 安全解析 Long (解析失败返回 null) */
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
