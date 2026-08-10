package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售汇总报表(经营看板顶部卡片 + Agent 经营问答单行结果);聚合指定时间范围核心销售 KPI.
 * <p>统计口径:
 * <ul>
 *   <li>totalGmv (总 GMV): SUM(order_info.pay_amount),order_status IN (PAID, SHIPPED, COMPLETED, PARTIAL_REFUND);基于 pay_time.</li>
 *   <li>orderCount: COUNT(DISTINCT order_info.id),同订单集(不含 PENDING/CANCELED).</li>
 *   <li>avgOrderValue: totalGmv / orderCount(报表层计算).</li>
 *   <li>refundRate: refundAmount / totalGmv * 100;百分比精度 2 位小数.</li>
 *   <li>refundAmount: SUM(refund_info.refund_amount) WHERE refund_status = REFUNDED,按 refund_time.</li>
 *   <li>totalDiscount: SUM(order_info.discount_amount),同 totalGmv 订单集.</li>
 * </ul>
 * <p>排除条件: tenant_id 过滤;store_id 非空时门店过滤;PENDING 待付不计 GMV 与订单数.
 * <p>返回值: 单行(无 GROUP BY);缺省近 30 天.
 */
@Data
public class SalesSummaryResp {

    /** 总 GMV(实付金额合计,仅含已支付订单) */
    private BigDecimal totalGmv;

    /** 订单总数(不含 pending 待付订单) */
    private Integer orderCount;

    /** 客单价 = 总GMV / 订单数 */
    private BigDecimal avgOrderValue;

    /** 退款率 = 退款金额 / 总GMV */
    private BigDecimal refundRate;

    /** 退款金额合计 */
    private BigDecimal refundAmount;

    /** 优惠金额合计(优惠券/促销抵扣总额) */
    private BigDecimal totalDiscount;
}
