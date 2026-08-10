package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 分类销售占比报表行项(经营看板 → 商品分类占比饼图/列表);按商品一级/二级分类聚合销售指标.
 * <p>统计口径:
 * <ul>
 *   <li>salesAmount: SUM(order_item.subtotal_amount);基于 order_item 关联的 product_info.category_id 聚合;pay_time 在范围.</li>
 *   <li>orderCount: COUNT(DISTINCT order_item.order_id),该分类至少有 1 件商品的订单.</li>
 *   <li>percentage: salesAmount / SUM(所有分类.salesAmount) * 100;所有行 percentage 求和 = 100%;categoryId 为空的历史订单归为"未分类"行.</li>
 * </ul>
 * <p>排除条件: 已删除分类(category.deleted = 1)仍展示历史数据;订单状态为 CANCELED(5) 不计入.
 * <p>返回值: 每行 = 1 个商品分类;默认按 salesAmount DESC;可切换层级(level=1 一级分类,level=2 二级分类).
 */
@Data
public class CategorySalesResp {

    /** 分类ID(可空,部分历史明细可能无分类ID) */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 该分类销售金额合计 */
    private BigDecimal salesAmount;

    /** 该分类关联订单数 */
    private Integer orderCount;

    /** 占总销售额百分比(0-100) */
    private BigDecimal percentage;
}
