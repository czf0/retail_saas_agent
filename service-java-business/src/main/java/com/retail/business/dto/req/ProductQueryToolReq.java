package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 工具专用入参: 商品查询工具(product:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>商品定位: 支持 keyword/category/brand 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class ProductQueryToolReq {

    private String keyword;

    private String category;

    /** 目标分类 id, 对应 product_category.id. */
    private Long categoryId;

    /** ProductStatus 枚举 code: 1=ON_SHELF 上架 0=OFF_SHELF 下架. */
    private Integer status;

    /** 仅查询低于安全库存的商品标记; true=开启过滤, false/null=不过滤. */
    private Boolean lowStockOnly;

    /** 仅查询有库存的商品标记(stockQty > 0); true=开启过滤, false/null=不过滤. */
    private Boolean inStock;

    /** 清仓标记过滤; true=仅清仓商品, false/null=不过滤. */
    private Boolean clearance;

    private String brand;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 售价下限(含), 与 maxPrice 组合区间查询. */
    private BigDecimal minPrice;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 售价上限(含), 与 minPrice 组合区间查询. */
    private BigDecimal maxPrice;

    /** 创建起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String createTimeStart;

    /** 创建截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String createTimeEnd;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
