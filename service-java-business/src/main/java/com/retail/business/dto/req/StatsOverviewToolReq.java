package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 经营概览查询工具(stats:overview, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页聚合报表).
 * <p>支持按时间范围与门店过滤各指标计数, 均可选(空=不限, 返回全量概览).
 */
@Data
public class StatsOverviewToolReq {

    /** 统计起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 统计截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    /** 目标门店 id, 对应 sys_store.id; 可空. */
    private Long storeId;
}
