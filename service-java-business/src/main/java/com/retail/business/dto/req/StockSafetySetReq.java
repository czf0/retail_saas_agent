package com.retail.business.dto.req;

import lombok.Data;

/**
 * 安全库存阈值设置请求(库存管理 -> 调安全库存/Agent 设安全库存工具).
 * <p>对应 Service 方法: StockService.setSafetyStock(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>商品定位使用业务语义(productName/skuCode), 门店维度默认当前用户门店, 也可用 storeName 指定.
 */
@Data
public class StockSafetySetReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 有规格商品定位. */
    private Long skuId;

    private String skuCode;

    /** 新的安全库存预警阈值; 非负整数; 当可用库存 <= 此值时触发临期预警, 0=不预警. */
    private Integer safetyStock;

    private String storeName;
}
