package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 财务汇总报表行项(运营后台经营看板 → 财务概览卡片);聚合指定时间范围内的订单收入,退款,优惠核心指标.
 * <p>统计口径:
 * <ul>
 *   <li>totalRevenue (总收入): SUM(order_info.pay_amount),仅 order_status IN (PAID, SHIPPED, COMPLETED, PARTIAL_REFUND),基于 pay_time 支付时间分桶.</li>
 *   <li>refundAmount (退款金额): SUM(refund_info.refund_amount) WHERE refund_status = REFUNDED(已退款),基于 refund_time 退款完成时间分桶.</li>
 *   <li>netRevenue (净收入): totalRevenue - refundAmount(报表层计算,非 SQL 聚合).</li>
 *   <li>discountAmount (优惠金额): SUM(order_info.discount_amount),同 totalRevenue 订单集.</li>
 * </ul>
 * <p>排除条件: order_status = CANCELED(5) 订单;tenant_id = 当前上下文租户;store_id 非空时仅该门店数据(多门店隔离).
 * <p>返回值: 单行(无 GROUP BY);时间范围缺省 = 近 30 天(含今天).
 */
@Data
public class FinanceSummaryResp {

    /** 总收入(实付金额合计) */
    private BigDecimal totalRevenue;

    /** 退款金额合计 */
    private BigDecimal refundAmount;

    /** 净收入 = 总收入 - 退款金额 */
    private BigDecimal netRevenue;

    /** 优惠金额合计(优惠券/促销抵扣总额) */
    private BigDecimal discountAmount;

    /** 订单总数 */
    private Integer orderCount;
}
