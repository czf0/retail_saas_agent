package com.retail.business.dto.req;

import lombok.Data;

/**
 * 销售记录查询请求 (Agent 工具用, 包装 StatsService.querySales 的参数).
 * <p>
 * StatsService.querySales 原始签名为 (String startDate, String endDate),
 * Agent 工具需要 DTO 包装以便生成 JSON Schema 供 Python Pydantic 使用.
 */
@Data
public class SalesQueryReq {

    /** 起始日期 (yyyy-MM-dd, 可空=不限) */
    private String startDate;

    /** 截止日期 (yyyy-MM-dd, 可空=不限) */
    private String endDate;
}
