package com.retail.business.dto.req;

import lombok.Data;

/**
 * 经营概览查询请求(Dashboard 首页概览卡片/Agent 经营概览工具).
 * <p>分页参数不适用(概览为聚合计数), 本 Req 不承载分页.
 * <p>供 HTTP 接口与 stats:overview 工具共同复用.
 */
@Data
public class StatsOverviewReq {

    /** 统计起始日期(yyyy-MM-dd, Asia/Shanghai); 按指标创建时间过滤. */
    private String startDate;

    /** 统计截止日期(yyyy-MM-dd, Asia/Shanghai); 按指标创建时间过滤. */
    private String endDate;

    /** 目标门店 id, 对应 sys_store.id; 可空, 对商品/促销/评价等门店维度指标过滤. */
    private Long storeId;
}
