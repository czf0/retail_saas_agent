package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情页(PDP)展示响应;聚合 SPU 主信息 + 多 SKU 列表(ProductSkuResp,当前列表不含;另接口查询)+ 分类名 + 品牌 + 可售库存.
 * <p>Controller: GET /api/v1/products/{id:\\d+};{id} 正则守卫(铁律 26);详情页返回 price=SPU 默认 SKU 价,多 SKU 点击切换后重新查 ProductSkuResp.
 */
@Data
public class ProductResp {

    private Long id;

    private String name;

    /** 商品分类外键(product_category.id);NULL = 未分类,前台列表中归为"其他". */
    private Long categoryId;

    /** 分类名称冗余(Service 层查询回填,避免前端 N+1) */
    private String category;

    /** 商品SPU编码(业务唯一键) */
    private String spuCode;

    /** 品牌名 */
    private String brand;

    /** SPU 默认售价(展示价,单位: 元;精度: 分,BigDecimal 2 dp);多 SKU 时取最低 SKU 价. */
    private BigDecimal price;

    /** 成本价(仅后台可见,前端敏感权限过滤,防止前端泄露毛利率).单位: 元,精度: 分. */
    private BigDecimal cost;

    /** 上下架状态:1=ON_SHELF(上架在售) 0=OFF_SHELF(下架);见 ProductStatusEnum. */
    private Integer status;

    private String description;

    private String imageUrl;

    /** 计算字段(SQL 聚合 product_stock SUM):当前可用库存总量(所有 SKU + 所有门店 + 租户级共享汇总). */
    private Integer stockQty;

    /** 安全库存阈值(SPU 级兜底;SKU 级有值时以 SKU 为准).低于此值触发库存预警. */
    private Integer safetyStock;

    private LocalDateTime createdAt;
}
