package com.retail.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.retail.rbac.dto.req.MenuCreateReq;
import com.retail.rbac.dto.req.MenuUpdateReq;
import com.retail.rbac.dto.resp.MenuResp;
import com.retail.rbac.dto.resp.MenuTreeResp;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.service.SysMenuService;
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
 * 菜单 / 权限管理接口.
 * <p>路由前缀 /api/v1/rbac/menus.sys_menu 表位于多租户 ignore-tables,平台级菜单(tenant_id=null)全局共享,
 * 租户级菜单(tenant_id!=null)仅本租户可见与分配.
 * <p>权限校验基于 @SaCheckPermission("rbac:menu:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(如 rbac:menu:list / query / add / edit / remove).
 * <p>注意:/tree 为字面量路径,须在 /{id} 之前注册;/{id} 加 \d+ 正则守卫,避免 "tree" 被误解析为 ID.
 */
@RestController
@RequestMapping("/api/v1/rbac/menus")
public class MenuController {

    private final SysMenuService sysMenuService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public MenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    /** 查询菜单平铺列表(不分层级,供后台表格维护). */
    @GetMapping("")
    @SaCheckPermission("rbac:menu:list")
    public R<List<MenuResp>> list() {
        return R.ok(sysMenuService.listMenus());
    }

    /**
     * 查询菜单树(按层级构建,供角色分配回显与前端路由生成).
     * <p>OR 模式:rbac:menu:query(菜单管理员)或 rbac:role:assign(角色分配时回显菜单树)均可访问.
     */
    @GetMapping("/tree")
    @SaCheckPermission(value = {"rbac:menu:query", "rbac:role:assign"}, mode = SaMode.OR)
    public R<List<MenuTreeResp>> tree() {
        return R.ok(sysMenuService.menuTree());
    }

    /**
     * 菜单详情.
     * <p>B-16 修复:{@code /{id}} 加 {@code \d+} 正则约束,避免 Spring 把 "tree" 当作 {id} 解析
     * 触发 MethodArgumentTypeMismatchException(与上方 {@code /tree} 字面量路径冲突).
     */
    @GetMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:menu:query")
    public R<MenuResp> get(@PathVariable("id") Long id) {
        return R.ok(sysMenuService.getMenu(id));
    }

    /** 创建菜单 / 权限按钮(支持目录,菜单,按钮三种类型). */
    @PostMapping("")
    @SaCheckPermission("rbac:menu:add")
    public R<MenuResp> create(@RequestBody MenuCreateReq req) {
        return R.ok(sysMenuService.createMenu(req));
    }

    /** 修改菜单(部分更新:名称 / 路径 / 组件 / 图标 / 排序 / 状态 / perms 等). */
    @PutMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:menu:edit")
    public R<MenuResp> update(@PathVariable("id") Long id, @RequestBody MenuUpdateReq req) {
        return R.ok(sysMenuService.updateMenu(id, req));
    }

    /** 删除菜单(逻辑删除 + 级联删除子菜单 + 级联清理角色-菜单关联). */
    @DeleteMapping("/{id:\\d+}")
    @SaCheckPermission("rbac:menu:remove")
    public R<OperationResultResp> delete(@PathVariable("id") Long id) {
        return R.ok(sysMenuService.deleteMenu(id));
    }
}
