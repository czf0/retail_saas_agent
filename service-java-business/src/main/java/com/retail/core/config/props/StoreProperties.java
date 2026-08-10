package com.retail.core.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 门店隔离配置.
 * <p>
 * 与 {@link TenantProperties} 对称,但采用<strong>白名单</strong>语义:仅 {@link #tables} 列出的表
 * 参与门店隔离(store_id 自动注入查询条件与插入值),其余表一律忽略.
 * <p>
 * 默认覆盖 sales_record / inventory_record / order_trend 三张统计快照表;
 * 未来新增门店级表只需在 application.yml 的 {@code store.tables} 追加表名,无需改代码.
 */
@Component
@ConfigurationProperties(prefix = "store")
public class StoreProperties {

    /** 门店隔离字段名 */
    private String column = "store_id";

    /** 参与门店隔离的表(白名单),逗号分隔 */
    private String tables = "sales_record,inventory_record,order_trend";

    /** 返回参与门店隔离的表列表 */
    public List<String> getStoreTableList() {
        return Arrays.asList(tables.split(","));
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getTables() {
        return tables;
    }

    public void setTables(String tables) {
        this.tables = tables;
    }
}
