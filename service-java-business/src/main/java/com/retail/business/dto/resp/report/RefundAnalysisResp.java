package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款分析报表行项(经营看板 → 售后分析卡片);聚合指定时间范围内退款单核心指标,单行汇总.
 * <p>统计口径:
 * <ul>
 *   <li>totalRefundAmount: SUM(refund_info.refund_amount) WHERE refund_status = REFUNDED(已退款完成),基于 refund_time 分桶.</li>
 *   <li>refundOrderCount: COUNT(DISTINCT refund_info.order_id) WHERE refund_status >= AUDIT_PASSED.</li>
 *   <li>fullRefundCount: COUNT(refund_info.id) WHERE refund_type = 'FULL'(全额退款).</li>
 *   <li>partialRefundCount: COUNT(refund_info.id) WHERE refund_type = 'PARTIAL'(部分退款).</li>
 *   <li>avgRefundAmount: totalRefundAmount / refundOrderCount(报表层,非 SQL AVG).</li>
 * </ul>
 * <p>排除条件: refund_status = REJECTED(审核拒绝) 不计入金额与订单数;tenant_id 过滤.
 * <p>返回值: 单行;缺省时间范围 = 近 30 天(按 refund_time).
 */
@Data
public class RefundAnalysisResp {

    /** 退款总金额 */
    private BigDecimal totalRefundAmount;

    /** 退款订单总数 */
    private Integer refundOrderCount;

    /** 全额退款笔数 */
    private Integer fullRefundCount;

    /** 部分退款笔数 */
    private Integer partialRefundCount;

    /** 平均退款金额 = 退款总金额 / 退款订单数 */
    private BigDecimal avgRefundAmount;
}
