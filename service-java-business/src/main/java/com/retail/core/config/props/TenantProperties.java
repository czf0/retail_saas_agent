package com.retail.core.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 多租户隔离配置 (绑定 application.yml 的 {@code tenant.*} 前缀).
 * <p>触发时机: 容器启动时由 Spring Boot 绑定注入, 供 {@code TenantInterceptor} 读取租户过滤字段名与忽略表.
 * <p>解决的问题: 集中管理租户列名与 ignore-tables 黑名单, 使拦截器无需硬编码;
 * 新增平台级表只需在配置追加表名, 不改代码.
 * <p>使用约束: {@code ignoreTables} 为黑名单语义 (黑名单内表不做 tenant_id 过滤);
 * 与门店白名单 {@code StoreProperties.tables} 语义相反, 勿混淆.
 */
@Component
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {
    // 多租户字段名
    private String column = "tenant_id";
    // 忽略租户过滤的表,逗号分隔
    private String ignoreTables = "sys_tenant";

    public List<String> getIgnoreTableList() {
        return Arrays.asList(ignoreTables.split(","));
    }

    // getter setter
    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(String ignoreTables) {
        this.ignoreTables = ignoreTables;
    }
}
