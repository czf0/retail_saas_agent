package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存资金占用报表行项(运营后台库存分析 → 库存资金占比饼图/列表);按商品维度统计当前库存价值与占比.
 * <p>统计口径:
 * <ul>
 *   <li>stockValue (库存价值): stockQty × unitCost;其中 unitCost = product_sku.cost(移动加权平均成本,采购入库后自动更新).</li>
 *   <li>percentage (占总资金比): stockValue / SUM(全部商品.stockValue) * 100;精度 2 位小数;所有行 percentage 求和 = 100%.</li>
 * </ul>
 * <p>排除条件: stockQty = 0 不展示;tenant_id 过滤;store_id 非空按门店隔离.
 * <p>返回值: 每行 = 1 个商品;默认按 stockValue DESC(帕累托 80/20 头部商品靠前).
 */
@Data
public class StockFundResp {

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 库存数量 */
    private Integer stockQty;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 库存价值 = 数量 × 单位成本 */
    private BigDecimal stockValue;

    /** 占总库存资金百分比(0-100) */
    private BigDecimal percentage;
}
