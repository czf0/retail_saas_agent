package com.retail.rbac.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 用户分配角色请求.
 */
@Data
public class AssignRoleReq {

    /** 角色 ID 列表(全量覆盖:未在列表中的旧关系将被删除) */
    private List<Long> roleIds;
}
