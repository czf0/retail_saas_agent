package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 用户分页查询请求(仅业务筛选字段).
 * <p>
 * 分页参数由 {@code PageParameterInterceptor} 从 {@code HttpServletRequest} 提取注入 ThreadLocal,
 * 业务 Req 不承载分页参数(分页为横切关注点).
 */
@Data
public class UserQueryReq {

    private String username;

    private String phone;

    private Integer status;

    /** 租户ID(平台管理员筛选指定租户用;租户管理员忽略此字段,仅查本租户) */
    private Long tenantId;

    private Long storeId;
}
