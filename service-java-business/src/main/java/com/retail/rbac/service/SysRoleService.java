package com.retail.rbac.service;

import com.retail.core.dto.PageResp;
import com.retail.rbac.dto.req.RoleCreateReq;
import com.retail.rbac.dto.req.RoleQueryReq;
import com.retail.rbac.dto.req.RoleUpdateReq;
import com.retail.rbac.dto.resp.OperationResultResp;
import com.retail.rbac.dto.resp.RoleResp;

import java.util.List;

/**
 * 角色服务(租户级).
 * <p>租户管理员可见本租户角色 + 平台内置角色;平台管理员可见全部.
 */
public interface SysRoleService {

    PageResp<RoleResp> listRoles(RoleQueryReq req);

    /**
     * 查询全部角色(不分页,供下拉选择器使用).
     * <p>租户管理员可见本租户角色 + 平台内置角色;平台管理员可见全部.
     * 与 {@link #listRoles} 共用过滤逻辑,仅去掉分页.
     */
    List<RoleResp> listAllRoles();

    RoleResp getRole(Long id);

    RoleResp createRole(RoleCreateReq req);

    RoleResp updateRole(Long id, RoleUpdateReq req);

    OperationResultResp deleteRole(Long id);

    /**
     * 分配菜单 (全量覆盖).
     * <p>前置条件: 角色必须存在, 否则抛 BizException; 传入的 menuIds 必须均为有效菜单, 否则抛 ParamException.
     * <p>副作用: 事务内删除旧菜单关联后重建, 即时影响该角色下所有用户的菜单与权限.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    void assignMenus(Long id, List<Long> menuIds);

    /** 查询角色已分配菜单ID */
    List<Long> getRoleMenuIds(Long id);
}
