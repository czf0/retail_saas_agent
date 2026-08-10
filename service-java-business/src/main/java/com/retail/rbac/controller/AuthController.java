package com.retail.rbac.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.retail.rbac.dto.req.LoginReq;
import com.retail.rbac.dto.resp.LoginResp;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.RouterResp;
import com.retail.rbac.dto.resp.UserInfo;
import com.retail.rbac.dto.resp.UserPermResp;
import com.retail.rbac.service.AuthService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口.
 * <p>路由前缀 /api/v1/auth.sys_user / sys_role / sys_menu 表位于多租户 ignore-tables,
 * 按业务键 tenant_id 显式操作,拦截器不自动附加 tenant_id 条件.
 * <p>login 已在 SaInterceptor 白名单(SaIgnore 跳过登录态);
 * me / logout / getInfo / getRouters 仅需登录态,无接口级权限注解(通过 Sa-Token StpUtil 全局校验).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 账号密码登录(公开路由,SaIgnore 跳过 SaInterceptor 登录校验). */
    @SaIgnore
    @PostMapping("/login")
    public R<LoginResp> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req));
    }

    /** 获取当前登录用户信息(含用户名 / 昵称 / 头像 / 租户 / 门店). */
    @GetMapping("/me")
    public R<UserInfo> me() {
        return R.ok(authService.currentUser());
    }

    /** 退出登录(清除 Sa-Token 会话 + 服务端状态). */
    @PostMapping("/logout")
    public R<OperationResultResp> logout() {
        authService.logout();
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("退出登录成功");
        return R.ok(resp);
    }

    /** 用户权限信息:用户 + 角色 + 权限标识 */
    @GetMapping("/getInfo")
    public R<UserPermResp> getInfo() {
        return R.ok(authService.getUserPermInfo());
    }

    /** 前端路由树 */
    @GetMapping("/getRouters")
    public R<List<RouterResp>> getRouters() {
        return R.ok(authService.getRouters());
    }
}
