package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新商品 SKU 请求,全部字段可空(部分更新).
 * <p>skuCode,specJson 创建后不可修改,故不在更新请求中.
 */
@Data
public class ProductSkuUpdateReq {

    /** SKU名称 */
    private String skuName;

    /** SKU售价 */
    private BigDecimal price;

    /** SKU成本 */
    private BigDecimal cost;

    /** 上下架状态:on_shelf / off_shelf */
    private Integer status;

    /** 库存数量 */
    private Integer stockQty;
}
