package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细项请求(创建订单时随订单提交).
 * <p>productId 与 skuId 二选一:有规格商品填 skuId,无规格商品填 productId.
 */
@Data
public class OrderItemReq {

    /** 商品ID */
    private Long productId;

    /** SKU ID(有规格商品时填,无规格商品为 NULL) */
    private Long skuId;

    /** 购买数量(必须 > 0) */
    private Integer qty;

    /** 单价(可空,为空时由 Service 从 product_info/product_sku 取价) */
    private BigDecimal unitPrice;
}
