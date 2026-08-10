package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品调价请求体, 运营后台商品管理 -> 调价对话框提交的新售价/新成本/原因.
 * <p>对应 Controller 路由: POST /api/v1/products/{productId:\d+}/price-adjust; 复用调价 Service 定位单商品.
 */
@Data
public class ProductPriceAdjustReq {

    /** 新售价(单位: 元, 精度: 分, BigDecimal 2dp); 必填, 至少传 newPrice/newCost 之一. */
    private BigDecimal newPrice;

    /** 新成本价(单位: 元, 精度: 分, BigDecimal 2dp); 可选, 内部核算用. */
    private BigDecimal newCost;

    /** 调价原因(可选), 供审计追溯. */
    private String reason;
}
