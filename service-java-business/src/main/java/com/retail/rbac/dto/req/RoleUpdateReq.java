package com.retail.rbac.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 角色修改请求(部分更新,null 字段不更新).
 */
@Data
public class RoleUpdateReq {

    private String roleName;

    private Integer roleSort;

    private Integer dataScope;

    private Integer status;

    private String remark;

    /** 重新分配的菜单ID列表(非 null 才更新) */
    private List<Long> menuIds;
}
