package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存周转率报表行项(运营后台库存分析 → 周转率排行);按商品维度计算近 N 天的库存周转效率.
 * <p>统计口径:
 * <ul>
 *   <li>outboundCost (出库成本合计): SUM(stock_movement.change_qty * product_sku.cost) WHERE movement_type = 'outbound' 且 biz_type IN ('order','refund') 且时间在范围.</li>
 *   <li>avgStockValue (平均库存价值): (期初库存价值 + 期末库存价值)/ 2;或近 30 天日末库存均值,取决于查询精度.</li>
 *   <li>turnoverRate (周转率): outboundCost / avgStockValue;精度 2 位小数.周转率 = 12 表示该时间窗口库存完整周转 12 次.</li>
 * </ul>
 * <p>排除条件: 已停用商品 status = OFF_SHELF 不参与排行(但库存仍会出现在价值统计中);store_id 非空时按门店隔离.
 * <p>返回值: 每行 = 1 个商品(按 product_id GROUP BY);默认按 turnoverRate DESC 降序;缺省时间 = 近 90 天.
 */
@Data
public class InventoryTurnoverResp {

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 出库成本合计(出库数量 × 成本价) */
    private BigDecimal outboundCost;

    /** 平均库存价值(当前库存数量 × 成本价) */
    private BigDecimal avgStockValue;

    /** 周转率 = 出库成本 / 平均库存价值 */
    private BigDecimal turnoverRate;
}
