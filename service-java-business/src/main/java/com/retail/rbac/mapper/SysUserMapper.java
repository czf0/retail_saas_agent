package com.retail.rbac.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.rbac.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper. 
 * <p>sys_user 在 ignore-tables 中, 查询不自动注入 tenant_id, 登录按 username 全局查找无需租户. 
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 按用户名查找(登录用, 忽略租户隔离, username 全局唯一) */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(@Param("username") String username);
}
