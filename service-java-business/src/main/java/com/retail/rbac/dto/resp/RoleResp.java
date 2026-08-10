package com.retail.rbac.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色详情页展示响应;聚合角色基础信息 + 数据权限范围 + 状态 + 已分配菜单ID列表(编辑回显用).
 * <p>Controller: GET /api/v1/system/roles/{id:\\d+};{id} 正则守卫.系统内置角色(如 admin)不可删除但可查看详情.
 */
@Data
public class RoleResp {

    private Long id;

    /** 租户外键(sys_tenant.id);平台级内置角色(跨租户) tenantId = NULL. */
    private Long tenantId;

    /** 角色展示名(中文,如"门店运营管理员";前端权限管理列表展示). */
    private String roleName;

    /** 角色英文唯一键(如 'ROLE_STORE_MANAGER';代码判断权限用;注解 @SaRole 使用此值). */
    private String roleKey;

    /** 角色排序号(前端列表展示顺序;升序 ASC;越小越靠前). */
    private Integer roleSort;

    /** 数据权限范围:1=ALL(全部) 2=TENANT(本租户) 3=STORE(本门店) 4=DEPT(本部门) 5=SELF(仅本人);见 DataScope. */
    private Integer dataScope;

    /** 角色状态:1=ENABLED(启用) 0=DISABLED(停用);停用角色不参与权限计算(等同于无角色). */
    private Integer status;

    private String remark;

    /** 已分配菜单ID列表(1:N,sys_role_menu 中间表聚合;前端权限树编辑回显用). */
    private List<Long> menuIds;

    private LocalDateTime createdAt;
}
