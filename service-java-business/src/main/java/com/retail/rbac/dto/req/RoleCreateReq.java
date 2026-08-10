package com.retail.rbac.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 角色新增请求.
 * <p>tenantId 由 MetaObjectHandler 自动植入(租户管理员创建时取当前租户);
 * 平台内置角色由初始化器插入,tenant_id=NULL.
 */
@Data
public class RoleCreateReq {

    private String roleName;

    private String roleKey;

    private Integer roleSort;

    /** 数据权限范围:1全部 2自定义 5仅本人 */
    private Integer dataScope;

    private String remark;

    /** 创建时分配的菜单ID列表 */
    private List<Long> menuIds;
}
