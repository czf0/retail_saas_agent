package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.FinanceSummaryResp;
import com.retail.business.dto.resp.report.PayTypeDistResp;

import java.util.List;

/**
 * 财务报表 Service.
 * <p>整合 order_info / order_refund 数据,提供 2 类财务分析报表:
 * 财务汇总,支付方式分布.
 */
public interface FinanceReportService {

    /**
     * 财务汇总:总收入 / 退款金额 / 净收入 / 优惠金额 / 订单数.
     *
     * @param req 时间范围 + 过滤参数
     * @return 财务汇总数据
     */
    FinanceSummaryResp getFinanceSummary(ReportTimeRangeReq req);

    /**
     * 支付方式分布:按微信/支付宝/余额/现金聚合金额与订单数,计算占比.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各支付方式分布列表
     */
    List<PayTypeDistResp> getPayTypeDist(ReportTimeRangeReq req);
}
