package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具专用入参: 商品上架工具(product:on-shelf, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: ProductService.onShelf(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 * <p>商品定位: 支持 productId/name/spuCode 多维 + 批量(names/productIds), 不只依赖 productId(铁律 20).
 */
@Data
public class ProductOnShelfToolReq {

    /** 目标商品 id, 对应 product.id; 优先使用, 单商品定位. */
    private Long productId;

    private String name;

    private String spuCode;

    /** 批量商品名列表; 用户列了多个名字时使用, 批量上架. */
    private List<String> names;

    /** 批量商品 id 列表, 对应 product.id; 批量上架使用. */
    private List<Long> productIds;
}
