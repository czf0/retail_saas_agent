package com.retail.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.RoleCreateReq;
import com.retail.rbac.dto.req.RoleQueryReq;
import com.retail.rbac.dto.req.RoleUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.RoleResp;
import com.retail.rbac.service.SysRoleService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口.
 * <p>路由前缀 /api/v1/rbac/roles.sys_role 表位于多租户 ignore-tables,按业务键 tenant_id 显式操作,
 * 平台管理员可创建平台级 / 租户级角色,租户管理员仅本租户角色.
 * <p>权限校验基于 @SaCheckPermission("rbac:role:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(如 rbac:role:list / query / add / edit / remove / assign).
 * <p>注意:/all 为字面量路径,须在 /{id} 之前注册;/{id} 加 \d+ 正则守卫,避免 "all" 被误解析为 ID.
 */
@RestController
@RequestMapping("/api/v1/rbac/roles")
public class RoleController {

    private final SysRoleService sysRoleService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public RoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    /**
     * 分页查询角色列表(按租户 / 关键词 / 状态过滤).
     * <p>分页参数 page/pageSize 由 PageParameterInterceptor 从 HttpServletRequest 提取注入 ThreadLocal,
     * Controller 不再承载分页参数(分页为横切关注点).
     */
    @GetMapping("")
    @SaCheckPermission("rbac:role:list")
    public R<PageResp<RoleResp>> list(RoleQueryReq req) {
        return R.ok(sysRoleService.listRoles(req));
    }

    /**
     * 全量角色列表(不分页,供下拉选择器使用).
     * <p>必须声明在 {@code /{id}} 之前,且 {@code /{id}} 加 {@code \d+} 正则约束,
     * 否则 Spring 会把 "all" 当作 {id} 解析触发 MethodArgumentTypeMismatchException.
     */
    @GetMapping("/all")
    @SaCheckPermission("rbac:role:list")
    public R<List<RoleResp>> listAll() {
        return R.ok(sysRoleService.listAllRoles());
    }

    /** 查询角色详情(含角色名称 / 编码 / 数据权限范围 / 状态). */
    @GetMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:role:query")
    public R<RoleResp> get(@PathVariable("id") Long id) {
        return R.ok(sysRoleService.getRole(id));
    }

    /** 创建角色(状态默认启用,数据权限缺省为本租户 ALL). */
    @PostMapping("")
    @SaCheckPermission("rbac:role:add")
    public R<RoleResp> create(@RequestBody RoleCreateReq req) {
        return R.ok(sysRoleService.createRole(req));
    }

    /** 修改角色(部分更新:名称 / 编码 / 备注 / 状态 / 数据权限). */
    @PutMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:role:edit")
    public R<RoleResp> update(@PathVariable("id") Long id, @RequestBody RoleUpdateReq req) {
        return R.ok(sysRoleService.updateRole(id, req));
    }

    /** 删除角色(逻辑删除 + 级联删除角色-菜单 / 用户-角色关联). */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:role:remove")
    public R<OperationResultResp> delete(@PathVariable("id") Long id) {
        return R.ok(sysRoleService.deleteRole(id));
    }

    /** 分配角色菜单权限(覆盖式写入,先删后插保证幂等). */
    @PutMapping("/{id:\\d+}/menus")
    @SaCheckPermission("rbac:role:assign")
    public R<OperationResultResp> assignMenus(@PathVariable("id") Long id, @RequestBody List<Long> menuIds) {
        sysRoleService.assignMenus(id, menuIds);
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(true);
        resp.setMessage("菜单分配成功");
        return R.ok(resp);
    }

    /** 查询角色已分配的菜单 ID 列表(前端回显分配状态). */
    @GetMapping("/{id:\\d+}/menus")
    @SaCheckPermission("rbac:role:query")
    public R<List<Long>> getRoleMenus(@PathVariable("id") Long id) {
        return R.ok(sysRoleService.getRoleMenuIds(id));
    }
}
