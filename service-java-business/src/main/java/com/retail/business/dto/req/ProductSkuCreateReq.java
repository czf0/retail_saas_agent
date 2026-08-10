package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品 SKU 创建请求(运营后台商品管理 -> SPU 详情 -> 新增 SKU).
 * <p>对应 Controller 路由: POST /api/v1/products/{productId:\\d+}/skus; status 字段 Service 层赋默认值(SkuStatus.ON_SALE=1, 铁律 6),
 * CreateReq 不承载 status.
 */
@Data
public class ProductSkuCreateReq {

    /** 所属 SPU id, 对应 product.id; 必填. */
    private Long productId;

    private String skuCode;

    private String skuName;

    /** 规格键值对 JSON; 如 {"颜色":"红","尺寸":"XL"}; Map 的 key 为规格名, value 为规格值. */
    private Map<String, String> specJson;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); SKU 实际售价, 可与 SPU price 不同. */
    private BigDecimal price;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); SKU 成本价(内部核算用). */
    private BigDecimal cost;

    /** 初始库存数量; 非负整数. */
    private Integer stockQty;
}
