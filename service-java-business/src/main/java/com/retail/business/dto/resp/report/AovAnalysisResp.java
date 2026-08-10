package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客单价分析报表行项(经营看板 → 客单价趋势/分桶);按时间分桶或会员等级分桶分析客单价与连带率.
 * <p>统计口径:
 * <ul>
 *   <li>avgOrderValue (客单价): totalGmv / orderCount,与 {@link SalesSummaryResp} 口径一致.</li>
 *   <li>avgItemsPerOrder (连带率/平均件数): SUM(order_item.qty) / COUNT(DISTINCT order_item.order_id);每单平均购买商品件数;辅助指标,判断促销是否提升了连带购买.</li>
 *   <li>refundRate: 同 SalesSummaryResp,退款率.</li>
 * </ul>
 * <p>排除条件: 同 SalesSummaryResp(CANCELED 不计,PENDING 不计).
 * <p>返回值: 每行 = 1 个分桶(时间桶 或 会员等级桶);可切换 groupBy 参数(date / memberLevel).
 */
@Data
public class AovAnalysisResp {

    /** 总 GMV */
    private BigDecimal totalGmv;

    /** 订单总数 */
    private Integer orderCount;

    /** 客单价 */
    private BigDecimal avgOrderValue;

    /** 退款率 */
    private BigDecimal refundRate;

    /** 平均订单商品数(每单平均购买件数) */
    private BigDecimal avgItemsPerOrder;
}
