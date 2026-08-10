package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 商品删除工具(product:delete, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: ProductService.delete(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 * <p>商品定位: 支持 productId/name/spuCode 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class ProductDeleteToolReq {

    /** 目标商品 id, 对应 product.id; 优先使用. */
    private Long productId;

    private String name;

    private String spuCode;
}
