package com.retail.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.retail.core.tenant.StoreLineHandler;
import com.retail.core.tenant.TenantInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器配置.
 * <p>
 * 注册两个 {@link TenantLineInnerInterceptor} 实例,分别处理 tenant_id 与 store_id 列:
 * <ol>
 *   <li>多租户插件(黑名单:ignore-tables 之外的表注入 tenant_id)</li>
 *   <li>门店隔离插件(白名单:仅 store.tables 中的表注入 store_id,store_id 为空时自动跳过)</li>
 * </ol>
 * 两者注入不同列互不干扰,按"范围从大到小"排列(先租户后门店).
 */
@Configuration
public class MybatisConfig {

    private final TenantInterceptor tenantInterceptor;
    private final StoreLineHandler storeLineHandler;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public MybatisConfig(TenantInterceptor tenantInterceptor, StoreLineHandler storeLineHandler) {
        this.tenantInterceptor = tenantInterceptor;
        this.storeLineHandler = storeLineHandler;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件(覆盖范围最广,先添加先生效)
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantInterceptor));
        // 门店隔离插件(仅对 store.tables 白名单表生效,store_id 为空时自动跳过)
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(storeLineHandler));
        // 分页插件(最后添加:基于已注入租户/门店隔离条件的 SQL 再拼 LIMIT,避免 count 跨租户统计)
        // setMaxLimit 兜底防恶意大分页(即便前端传 pageSize=9999 也被截断为 500)
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        pageInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }
}
