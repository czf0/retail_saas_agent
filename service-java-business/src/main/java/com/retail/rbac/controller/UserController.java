package com.retail.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.AssignRoleReq;
import com.retail.rbac.dto.req.ResetPwdReq;
import com.retail.rbac.dto.req.UserCreateReq;
import com.retail.rbac.dto.req.UserQueryReq;
import com.retail.rbac.dto.req.UserUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.UserResp;
import com.retail.rbac.service.SysUserService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口.
 * <p>路由前缀 /api/v1/rbac/users.sys_user 表位于多租户 ignore-tables,按业务键 tenant_id 显式操作,
 * 拦截器不自动附加 tenant_id 条件;平台管理员可见全平台用户,租户管理员仅本租户用户.
 * <p>权限校验基于 @SaCheckPermission("rbac:user:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(如 rbac:user:add / list / query / edit / remove / assign / reset);
 * 超级管理员(roleKey=admin)返回 ["*"] 全放行.
 */
@RestController
@RequestMapping("/api/v1/rbac/users")
public class UserController {

    private final SysUserService sysUserService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /** 分页查询用户列表(按租户 / 门店 / 关键词 / 状态过滤). */
    @GetMapping("")
    @SaCheckPermission("rbac:user:list")
    public R<PageResp<UserResp>> list(UserQueryReq req) {
        return R.ok(sysUserService.listUsers(req));
    }

    /** 查询用户详情(含角色,门店,权限等信息). */
    @GetMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:user:query")
    public R<UserResp> get(@PathVariable("id") Long id) {
        return R.ok(sysUserService.getUser(id));
    }

    /** 创建用户(含初始密码,状态默认启用). */
    @PostMapping("")
    @SaCheckPermission("rbac:user:add")
    public R<UserResp> create(@RequestBody UserCreateReq req) {
        return R.ok(sysUserService.createUser(req));
    }

    /** 修改用户(部分更新:姓名 / 手机号 / 邮箱 / 状态 / 门店等). */
    @PutMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:user:edit")
    public R<UserResp> update(@PathVariable("id") Long id, @RequestBody UserUpdateReq req) {
        return R.ok(sysUserService.updateUser(id, req));
    }

    /** 删除用户(逻辑删除 + 级联禁用用户登录态). */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:user:remove")
    public R<OperationResultResp> delete(@PathVariable("id") Long id) {
        return R.ok(sysUserService.deleteUser(id));
    }

    /** 分配用户角色(覆盖式写入,先删后插保证幂等). */
    @PutMapping("/{id:\\d+}/roles")
    @SaCheckPermission("rbac:user:assign")
    public R<OperationResultResp> assignRoles(@PathVariable("id") Long id, @RequestBody AssignRoleReq req) {
        sysUserService.assignRoles(id, req);
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("角色分配成功");
        return R.ok(resp);
    }

    /** 重置用户密码(随机生成或指定,密码加密存储). */
    @PutMapping("/{id:\\d+}/password")
    @SaCheckPermission("rbac:user:reset")
    public R<OperationResultResp> resetPassword(@PathVariable("id") Long id, @RequestBody ResetPwdReq req) {
        sysUserService.resetPassword(id, req);
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("密码重置成功");
        return R.ok(resp);
    }
}
