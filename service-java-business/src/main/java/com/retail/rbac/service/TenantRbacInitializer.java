package com.retail.rbac.service;

/**
 * 租户 RBAC 初始化器(解耦关键接口).
 * <p>
 * 定义于 rbac 模块,由 rbac 提供实现.business 层的 {@code TenantConfigService} 在创建租户时
 * 调用此接口,自动为新租户创建「租户管理员」角色并分配默认菜单权限.
 * <p>
 * 微服务化后可替换为 Feign 远程调用实现,调用方(business)无需感知实现细节.
 * <p>
 * <b>幂等性</b>:若租户管理员角色已存在则跳过创建,可安全重复调用.
 */
public interface TenantRbacInitializer {

    /**
     * 为新租户初始化 RBAC 数据:
     * <ol>
     *   <li>创建 role_key='tenant_admin' 的租户管理员角色(tenant_id = 参数值)</li>
     *   <li>分配默认菜单权限(排除菜单管理,菜单为全局共享仅平台管理员可管理)</li>
     * </ol>
     *
     * @param tenantId 新租户的业务 ID
     */
    void initTenantRbac(Long tenantId);
}
