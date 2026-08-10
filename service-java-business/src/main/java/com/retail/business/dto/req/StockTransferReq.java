package com.retail.business.dto.req;

import lombok.Data;

/**
 * 门店间调拨请求(源门店出库+目标门店入库, 同单据号关联成对流水/Agent 调拨工具).
 * <p>对应 Service 方法: StockService.transfer(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class StockTransferReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 productName 反查定位. */
    private Long productId;

    private String productName;

    /** 目标 SKU id, 对应 product_sku.id; 可选, 优先使用; 有规格商品定位. */
    private Long skuId;

    private String skuCode;

    /** 调拨数量; 正整数, 不能为 0 或负数; Service 层校验源门店可用库存充足. */
    private Integer qty;

    /** 源门店名称(如「城西店」); 与 fromStoreId 二选一, Service 层按名称反查 id. */
    private String fromStoreName;

    /** 目标门店名称(如「滨江店」); 与 toStoreId 二选一, Service 层按名称反查 id. */
    private String toStoreName;

    private String remark;
}
