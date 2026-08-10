package com.retail.rbac.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户详情页展示响应;聚合账号基础信息 + 租户/门店归属 + 性别/状态 + 已分配角色ID列表(编辑回显用).
 * <p>Controller: GET /api/v1/system/users/{id:\\d+};{id} 正则守卫.敏感字段 password/salt 永不返回.
 */
@Data
public class UserResp {

    private Long id;

    /** 租户外键(sys_tenant.id);平台管理员 tenantId = NULL,可跨租户管理. */
    private Long tenantId;

    /** 门店外键(sys_store.id);NULL = 租户级通用账号,不绑定具体门店. */
    private Long storeId;

    private String username;

    private String nickName;

    private String email;

    private String phone;

    /** 性别:0=未知 1=男 2=女(字典 sys_user_sex;可扩展). */
    private Integer gender;

    /** 账号状态:1=ENABLED(启用,可登录) 0=DISABLED(禁用,登录直接拒绝);见 SysStatus. */
    private Integer status;

    /** 最后登录成功时间(仅登录成功时更新;登录失败不计;用于安全审计展示异地登录). */
    private LocalDateTime lastLoginAt;

    private String remark;

    /** 已分配角色ID列表(1:N,sys_user_role 中间表查询聚合;编辑时前端传入回写). */
    private List<Long> roleIds;

    private LocalDateTime createdAt;
}
