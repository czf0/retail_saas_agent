package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品 SPU 更新请求, 运营后台商品管理 -> 编辑商品, 部分更新名称/价格/成本/库存/安全库存/分类等.
 * <p>对应 Controller 路由: PUT /api/v1/products/{productId:\d+}; {productId} 由 PathVariable 正则守卫(铁律 26).
 * <p>status 编辑场景可更新, 由 Service 层经 EnumUtil.fromCode 转换为 ProductStatus(铁律 10).
 */
@Data
public class ProductUpdateReq {

    private String name;

    private Long categoryId;

    private String category;

    /** 商品SPU编码 */
    private String spuCode;

    /** 品牌名 */
    private String brand;

    private BigDecimal price;

    private BigDecimal cost;

    private Integer status;

    private String description;

    private String imageUrl;

    private Integer stockQty;

    private Integer safetyStock;
}