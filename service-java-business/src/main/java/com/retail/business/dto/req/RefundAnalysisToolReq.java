package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 退款分析统计工具(refund:analysis, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页聚合报表).
 * <p>只读报表工具, 按时间范围统计退款总金额/笔数/全额与部分退款占比.
 */
@Data
public class RefundAnalysisToolReq {

    /** 统计起始日期(yyyy-MM-dd, Asia/Shanghai); 可空, 缺省=近 30 天起点. */
    private String startDate;

    /** 统计截止日期(yyyy-MM-dd, Asia/Shanghai); 可空, 缺省=今日. */
    private String endDate;

    private String orderNo;
}
