package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具专用入参: 商品下架工具(product:off-shelf, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: ProductService.offShelf(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 * <p>商品定位: 支持 productId/name/spuCode/brand/category 多维 + 批量(names/productIds), 不只依赖 productId(铁律 20).
 */
@Data
public class ProductOffShelfToolReq {

    /** 目标商品 id, 对应 product.id; 单商品定位, 三选一. */
    private Long productId;

    private String name;

    private String spuCode;

    private String brand;

    /** 目标分类 id, 对应 product_category.id. */
    private Long categoryId;

    private String category;

    /** 批量商品名列表; 用户列了多个名字时使用, 批量下架. */
    private List<String> names;

    /** 批量商品 id 列表, 对应 product.id; 批量下架使用. */
    private List<Long> productIds;

    private String reason;
}
