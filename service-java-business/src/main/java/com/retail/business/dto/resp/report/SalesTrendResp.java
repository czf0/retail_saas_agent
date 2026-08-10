package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售趋势报表行项(经营看板 → 销售趋势折线图);按时间分桶(日/周/月)聚合 GMV 与订单数,每行 = 1 个时间桶.
 * <p>统计口径:
 * <ul>
 *   <li>时间分桶粒度: 按 stat_date(yyyy-MM-dd)GROUP BY;周粒度 = DATE_TRUNC WEEK,月粒度 = DATE_TRUNC MONTH.</li>
 *   <li>salesAmount: SUM(order_info.pay_amount),基于 pay_time 支付时间分桶;order_status IN (PAID, SHIPPED, COMPLETED, PARTIAL_REFUND).</li>
 *   <li>orderCount: COUNT(DISTINCT order_info.id),同订单集.</li>
 * </ul>
 * <p>排除条件: order_status = CANCELED(5) / PENDING(1) 订单;tenant_id = 当前上下文租户;store_id 非空时门店过滤.
 * <p>返回值: 每行 = 1 个时间桶;按 date ASC 升序;缺省范围 = 近 30 天.
 */
@Data
public class SalesTrendResp {

    /** 日期(yyyy-MM-dd 格式字符串) */
    private String date;

    /** 当日销售金额合计 */
    private BigDecimal salesAmount;

    /** 当日订单数 */
    private Integer orderCount;
}
