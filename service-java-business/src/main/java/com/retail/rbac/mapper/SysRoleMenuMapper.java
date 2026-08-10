package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单关系 Mapper(物理删除). 
 * <p>关系表无 tenant_id, 忽略租户拦截; 分配菜单时先删后插. 
 */
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /** 查询角色已分配的菜单ID列表 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /** 按 role_id 物理删除所有关系(重新分配菜单前清理) */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
