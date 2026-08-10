package com.retail.business.dto.req;

import lombok.Data;

/**
 * 库存分页查询请求(库存管理列表页筛选/Agent 查库存工具).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 * <p>storeId 由门店拦截器自动按当前用户门店隔离, 传入仅用于平台管理员显式筛选.
 */
@Data
public class StockQueryReq {

    /** 目标商品 id, 对应 product.id. */
    private Long productId;

    /** 目标 SKU id, 对应 product_sku.id; 无规格商品为 null. */
    private Long skuId;

    /** 目标门店 id, 对应 sys_store.id; 可空, 平台管理员显式筛选用. */
    private Long storeId;

    private String storeName;

    /** 仅查询低于安全库存的账户标记(available_qty < safety_stock); true=开启过滤, false/null=不过滤. */
    private Boolean lowStockOnly;

    /** 仅查询在途库存的账户标记(in_transit_qty > 0, 采购在途); true=开启过滤, false/null=不过滤. */
    private Boolean inTransitOnly;

    /** 仅查询高于安全库存的账户标记(available_qty > safety_stock, 库存积压分析); true=开启过滤, false/null=不过滤. */
    private Boolean highStockOnly;

    private String productName;

    private String brand;

    /** 目标分类 id, 对应 product_category.id. */
    private Long categoryId;

    private String category;

    /** ProductStatus 枚举 code: 1=ON_SHELF 上架 0=OFF_SHELF 下架. */
    private Integer status;
}
