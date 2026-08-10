package com.retail.business.dto.resp.report;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 滞销商品报表行项(运营后台库存分析 → 滞销商品列表);识别近 N 天无销售出库记录的高库存商品.
 * <p>统计口径:
 * <ul>
 *   <li>lastOutTime (最后出库时间): MAX(stock_movement.created_at) WHERE movement_type = 'outbound' AND biz_type = 'order'(排除调拔出库).</li>
 *   <li>daysNoSales (未销售天数): DATEDIFF(CURDATE(), lastOutTime);无出库记录 = DATEDIFF(CURDATE(), product_info.create_time).</li>
 *   <li>滞销阈值可配置: 默认 daysNoSales >= 60 天 且 stockQty > 0.</li>
 * </ul>
 * <p>排除条件: stockQty = 0 的商品不展示(已清库存不算滞销);已删除商品 deleted = 1.
 * <p>返回值: 每行 = 1 个商品(SPU 级,汇总所有 SKU 库存);按 daysNoSales DESC 降序;缺省阈值 = 60 天.
 */
@Data
public class SlowMovingResp {

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 最后一次出库时间(可空,表示从未出库) */
    private LocalDateTime lastOutTime;

    /** 当前库存数量 */
    private Integer stockQty;

    /** 未销售天数(最后出库时间至查询结束日的天数,无出库记录则从入库时间算起) */
    private Integer daysNoSales;
}
