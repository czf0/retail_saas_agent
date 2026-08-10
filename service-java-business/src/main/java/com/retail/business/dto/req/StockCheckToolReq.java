package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 库存查询工具(stock:check, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>商品定位: 支持 productId/productName/brand/category 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class StockCheckToolReq {

    /** 目标商品 id, 对应 product.id; 可空=不限. */
    private Long productId;

    /** 目标 SKU id, 对应 product_sku.id; 可空=不限. */
    private Long skuId;

    /** 目标门店 id, 对应 sys_store.id; 可空, 门店拦截器自动按当前用户门店隔离. */
    private Long storeId;

    private String storeName;

    /** 仅查询低于安全库存的账户标记; true=开启过滤, false/null=不过滤. */
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

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
