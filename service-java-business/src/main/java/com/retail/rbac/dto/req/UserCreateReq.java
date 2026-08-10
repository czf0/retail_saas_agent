package com.retail.rbac.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 系统用户创建请求(系统管理 -> 用户管理 -> 新增用户).
 * <p>对应 Controller 路由: POST /api/v1/rbac/users; status 字段 Service 层赋默认值(SysStatus.ENABLED=1, 铁律 6),
 * CreateReq 不承载 status.
 * <p>tenantId/storeId 由 MetaObjectHandler 自动植入(租户管理员创建时取当前上下文);
 * 平台管理员跨租户创建用户时显式传入 tenantId/storeId.
 */
@Data
public class UserCreateReq {

    /** 租户 id, 对应 sys_tenant.id; 平台管理员跨租户创建时显式指定; 租户管理员创建时留空(自动取当前租户). */
    private Long tenantId;

    /** 所属门店 id, 对应 sys_store.id; NULL=无固定门店. */
    private Long storeId;

    private String username;

    /** BCrypt 密码哈希(CreateReq 中此字段为前端传入明文密码, Service 层 BCrypt.hashpw 后存入 Entity); Resp/DTO 层绝不返回此字段. */
    private String password;

    private String nickName;

    private String email;

    private String phone;

    /** 性别 code: 0=未知 1=男 2=女. */
    private Integer gender;

    private String remark;

    /** 创建时分配的角色 id 列表, 对应 sys_role.id; 可空. */
    private List<Long> roleIds;
}
