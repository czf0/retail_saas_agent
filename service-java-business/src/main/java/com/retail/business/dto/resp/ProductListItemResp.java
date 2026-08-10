package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表页行项(管理后台商品管理列表,返回 20/页;前端点击行进入详情查询完整 ProductResp).
 * <p>统计辅助字段 belowSafety = 列表页 SQL 内嵌 CASE WHEN 实时计算;详情查询 ProductResp 不返回该字段以节省带宽.
 */
@Data
public class ProductListItemResp {

    private Long id;

    private String name;

    /** 分类外键(product_category.id);用于列表筛选 categoryId=X. */
    private Long categoryId;

    /** 分类名称冗余(Service 回填) */
    private String category;

    /** 商品SPU编码 */
    private String spuCode;

    /** 品牌名 */
    private String brand;

    /** 售价(单位: 元,精度: 分;BigDecimal(12,2)). */
    private BigDecimal price;

    /** 成本价(单位: 元,精度: 分;仅后台有权限可见). */
    private BigDecimal cost;

    /** 商品状态:1=上架 0=下架;见 ProductStatusEnum. */
    private Integer status;

    /** 可用库存(所有门店/仓库汇总,列表页简化展示单值) */
    private Integer stockQty;

    /** 安全库存阈值(SPU 级). */
    private Integer safetyStock;

    /** 计算字段(列表页 SQL 内嵌 CASE WHEN):true = stockQty < safetyStock;详情页无需此字段. */
    private Boolean belowSafety;
}
