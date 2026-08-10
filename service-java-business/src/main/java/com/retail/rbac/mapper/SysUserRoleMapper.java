package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysUserRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关系 Mapper(物理删除). 
 * <p>关系表无 tenant_id, 忽略租户拦截; 分配角色时先删后插. 
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /** 查询用户已分配的角色ID列表 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 按 user_id 物理删除所有关系(重新分配角色前清理) */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
