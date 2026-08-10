package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品分页查询请求(商品列表页筛选/Agent 查商品工具).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 */
@Data
public class ProductListReq {
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
    private String keyword;
    private String brand;
    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 售价下限(含), 与 maxPrice 组合区间查询. */
    private BigDecimal minPrice;
    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 售价上限(含), 与 minPrice 组合区间查询. */
    private BigDecimal maxPrice;
    /** 创建起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String createTimeStart;
    /** 创建截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String createTimeEnd;
}
