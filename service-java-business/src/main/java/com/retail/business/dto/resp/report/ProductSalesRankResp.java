package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品销售排行报表行项(经营看板 → 热销商品 Top N);按商品维度聚合销量与销售额,用于帕累托分析.
 * <p>统计口径:
 * <ul>
 *   <li>qty (销售数量): SUM(order_item.qty) WHERE pay_time 在范围;排除退款 qty(refund_item.refund_qty 反向扣减).</li>
 *   <li>salesAmount: SUM(order_item.subtotal_amount);净销售额 = 原价小计 - 退款扣减.</li>
 *   <li>rank (排名): 基于 salesAmount 排序的行号(ROW_NUMBER);并列销量同排名不跳号.</li>
 * </ul>
 * <p>排除条件: 退款审核通过(refund_status = REFUNDED)的明细从 qty 与 salesAmount 扣减;order_status = CANCELED 不计.
 * <p>返回值: 每行 = 1 个商品(SPU 级,product_id);默认按 salesAmount DESC;缺省返回 Top 100 行.
 */
@Data
public class ProductSalesRankResp {

    /** 商品ID */
    private Long productId;

    /** 商品名称(订单明细快照) */
    private String productName;

    /** 商品分类(订单明细快照) */
    private String category;

    /** 销售数量合计 */
    private Integer qty;

    /** 销售金额合计(小计金额之和) */
    private BigDecimal salesAmount;

    /** 排名(按销售金额降序) */
    private Integer rank;
}
