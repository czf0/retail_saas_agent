package com.retail.rbac.service;

/**
 * 租户名称解析接口(解耦关键).
 * <p>定义于 rbac 模块,由业务层({@code TenantConfigService})实现并注入,
 * 避免 rbac 反向依赖 business 模块.微服务化后可由远程调用实现替换.
 * <p>用于登录返回 tenantName 时反查租户显示名.
 */
public interface TenantNameResolver {

    /**
     * 根据租户ID解析租户显示名.
     *
     * @param tenantId 租户ID
     * @return 租户显示名,不存在返回 null
     */
    String resolve(Long tenantId);
}
