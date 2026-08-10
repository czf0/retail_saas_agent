package com.retail.core.tenant;

/**
 * 多租户/门店/角色上下文持有者 (ThreadLocal).
 * <p>触发时机: 每个 HTTP 请求由 {@code GlobalReqInterceptor.preHandle} 写入
 * (租户用户取 session 值, 平台管理员读 X-Tenant-Id 头), 请求结束时在 afterCompletion 清理.
 * <p>解决的问题: 在多租户架构下向 TenantInterceptor / StoreLineHandler / Service 层
 * 传递当前租户/门店/角色, 避免各处手动拼装 (铁律 16).
 * <p>使用约束: 请求结束必须调用 {@link #clear()}, 否则线程池复用会导致上下文串用
 * (A 租户数据泄漏到 B 租户); 非 HTTP 线程 (定时任务/初始化) 未设置时 get 返回 null.
 */
public class TenantContext {
    private static final ThreadLocal<String> TENANT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> STORE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE_HOLDER = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_HOLDER.get();
    }

    public static void setStoreId(String storeId) {
        STORE_HOLDER.set(storeId);
    }

    public static String getStoreId() {
        return STORE_HOLDER.get();
    }

    public static void setUserRole(String role) {
        USER_ROLE_HOLDER.set(role);
    }

    public static String getUserRole() {
        return USER_ROLE_HOLDER.get();
    }

    public static void clear() {
        TENANT_HOLDER.remove();
        STORE_HOLDER.remove();
        USER_ROLE_HOLDER.remove();
    }
}
