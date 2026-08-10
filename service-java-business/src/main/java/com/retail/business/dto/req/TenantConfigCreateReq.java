package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 租户配置创建请求, 平台超管功能: 初始化租户名称/每日 token 上限/可用工具与子流程, 并默认启用.
 * <p>对应 Controller 路由: POST /api/v1/tenants; 需 @SaCheckRole("admin") + @SaCheckPermission("business:tenant:add")(铁律 17).
 * <p>enabled 默认 true; tenant_id 由 Service 层生成/显式赋值, 拦截器不自动附加(多租户 ignore-tables, 铁律 16).
 */
@Data
public class TenantConfigCreateReq {

    private Long tenantId;

    private String tenantName;

    private Integer dailyTokenLimit = 500000;

    private List<String> allowedTools;

    private List<String> allowedSubflows;

    private Boolean enabled = true;
}