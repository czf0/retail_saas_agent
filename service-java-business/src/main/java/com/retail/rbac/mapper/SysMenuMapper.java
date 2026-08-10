package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单/权限 Mapper. 
 * <p>sys_menu 在 ignore-tables 中(全局共享无 tenant_id), 权限与路由查询跨租户定位用户菜单. 
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 查询用户权限标识列表(StpInterface.getPermissionList 用) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT m.perms FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND m.perms IS NOT NULL AND m.perms <> '' " +
            "AND m.status = 1 AND m.deleted = 0 AND r.status = 1 AND r.deleted = 0")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /** 查询用户可见菜单(M目录/C菜单, getRouters 路由树用) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND m.menu_type IN (1,2) " +  // 1=DIR(目录) 2=MENU(菜单)(MenuType, 旧 M/C)
            "AND m.visible = 1 AND m.status = 1 AND m.deleted = 0 AND r.status = 1 AND r.deleted = 0 " +
            "ORDER BY m.parent_id, m.order_num")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /** 查询全部菜单(平台菜单管理用) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_menu WHERE deleted = 0 ORDER BY parent_id, order_num")
    List<SysMenu> selectAllMenus();

    /** 查询角色已分配的菜单ID列表 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
