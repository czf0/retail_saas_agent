package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.AovAnalysisResp;
import com.retail.business.dto.resp.report.OrderFunnelResp;
import com.retail.business.dto.resp.report.RefundAnalysisResp;

import java.util.List;

/**
 * 订单报表 Service.
 * <p>整合 order_info / order_refund / order_item 数据,提供 3 类订单分析报表:
 * 订单转化漏斗,退款分析,客单价分析.
 */
public interface OrderReportService {

    /**
     * 订单转化漏斗:按状态阶段统计订单数与转化率.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各阶段漏斗数据列表
     */
    List<OrderFunnelResp> getOrderFunnel(ReportTimeRangeReq req);

    /**
     * 退款分析:退款总金额 / 笔数 / 全额与部分退款占比 / 平均退款金额.
     *
     * @param req 时间范围 + 过滤参数
     * @return 退款分析数据
     */
    RefundAnalysisResp getRefundAnalysis(ReportTimeRangeReq req);

    /**
     * 客单价分析:GMV / 订单数 / 客单价 / 退款率 / 平均订单商品数.
     *
     * @param req 时间范围 + 过滤参数
     * @return 客单价分析数据
     */
    AovAnalysisResp getAovAnalysis(ReportTimeRangeReq req);
}
