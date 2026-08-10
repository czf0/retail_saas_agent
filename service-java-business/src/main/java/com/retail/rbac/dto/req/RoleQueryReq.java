package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 角色分页查询请求(仅业务筛选字段).
 * <p>
 * 分页参数由 {@code PageParameterInterceptor} 从 {@code HttpServletRequest} 提取注入 ThreadLocal,
 * 业务 Req 不承载分页参数(分页为横切关注点).
 */
@Data
public class RoleQueryReq {

    /** 角色名称(模糊查询) */
    private String roleName;
}
