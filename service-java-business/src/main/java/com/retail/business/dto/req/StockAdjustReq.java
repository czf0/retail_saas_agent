package com.retail.business.dto.req;

import lombok.Data;

/**
 * 库存手动调整请求(库存管理 -> 库存调整/Agent 调库存工具).
 * <p>对应 Service 方法: StockService.adjust(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>changeQty 正数=增加库存, 负数=减少库存; bizType 默认 5=MANUAL, 可按业务场景指定.
 */
@Data
public class StockAdjustReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 否则用 skuCode 反查定位. */
    private Long skuId;

    private String skuCode;

    /** 目标门店 id, 对应 sys_store.id; 可空=租户中心仓; 门店用户由拦截器自动注入隔离. */
    private Long storeId;

    /** 变动数量; 正数=增加库存, 负数=减少库存, 不能为 0; Service 层校验超卖. */
    private Integer changeQty;

    private String reason;

    /** StockBizType 枚举 code: 1=ORDER 订单 2=PURCHASE 采购 3=ADJUST 调整 4=REFUND 退款 5=MANUAL 手工; 默认 5=MANUAL. */
    private Integer bizType = 5;
}
