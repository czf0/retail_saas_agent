package com.retail.business.dto.resp.report;

import lombok.Data;

/**
 * 库存安全预警报表行项(运营后台库存分析 → 缺货预警列表);筛选当前可用库存 < 安全库存的 SKU 明细.
 * <p>统计口径:
 * <ul>
 *   <li>stockQty (当前可用库存): product_stock.available_qty = 总库存 - 已预留(reservation),实时快照.</li>
 *   <li>safetyStock (安全库存阈值): 取 product_sku.safety_stock(SKU 级);若空则取 product_info.safety_stock(SPU 级兜底).</li>
 *   <li>belowSafety (预警触发): 计算字段 = stockQty < safetyStock;仅 true 的行返回(列表页前端已过滤,但报表仍显式返回便于二次校验).</li>
 * </ul>
 * <p>排除条件: 已下架商品 status = OFF_SHELF 仍预警(避免死库存漏提醒);store_id 非空时按门店过滤,空 = 租户级共享库存.
 * <p>返回值: 每行 = 1 个 SKU(库存账户维度);按(safetyStock - stockQty)DESC 紧急度排序.
 */
@Data
public class StockAlertResp {

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 当前可用库存 */
    private Integer stockQty;

    /** 安全库存阈值 */
    private Integer safetyStock;

    /** 是否低于安全库存 */
    private Boolean belowSafety;
}
