package com.retail.rbac.dto.resp;

import lombok.Data;

/**
 * 当前登录用户信息(登录 / me 接口返回).
 * <p>tenantId / tenantName 对平台管理员为 null.
 * role 为用户主角色 key(超级管理员为 "admin"),兼容旧前端字段.
 */
@Data
public class UserInfo {

    private Long userId;

    private String username;

    private String role;

    /** 租户ID,平台管理员为 null */
    private Long tenantId;

    /** 租户名称,平台管理员为 null */
    private String tenantName;

    private String displayName;
}
