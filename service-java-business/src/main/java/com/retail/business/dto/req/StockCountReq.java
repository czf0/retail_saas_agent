package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品盘点请求(盘点录入实盘数量, 自动计算盘盈/盘亏/Agent 盘点工具).
 * <p>对应 Service 方法: StockService.count(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>盘差 = actualQty - bookQty: 盘差>0 盘盈(CHECK_GAIN 流水), <0 盘亏(CHECK_LOSS 流水), =0 平账.
 */
@Data
public class StockCountReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 有规格商品定位. */
    private Long skuId;

    private String skuCode;

    /** 账面数量; 可空, 缺省由 Service 取当前可用库存. */
    private Integer bookQty;

    /** 实盘数量; 用户填报, 必填; 非负整数. */
    private Integer actualQty;

    private String remark;
}
