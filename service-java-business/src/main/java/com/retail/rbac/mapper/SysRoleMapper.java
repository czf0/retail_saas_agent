package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper. 
 * <p>sys_role 在 ignore-tables 中, 涉及用户的角色查询不自动注入 tenant_id(需跨租户定位用户角色). 
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询用户的角色 key 列表(StpInterface.getRoleList 用) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT r.role_key FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 查询用户的角色列表(含详情) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户角色的最小 data_scope(最广数据范围). 
     * <p>取 MIN 是因为"最广范围优先": 用户若同时拥有 ALL(1) 和 SELF(5) 角色, 按 ALL 放行. 
     *
     * @param userId 用户ID
     * @return 最小 data_scope 值(1=全部, 5=仅本人), 无角色返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT MIN(r.data_scope) FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0")
    Integer selectMinDataScopeByUserId(@Param("userId") Long userId);
}
