package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 商品详情查询工具(product:detail, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(单条详情查询).
 * <p>商品定位: 支持 productId/spuCode/name 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class ProductDetailToolReq {

    /** 目标商品 id, 对应 product.id; 可选, 优先使用; 否则用 spuCode/name 反查. */
    private Long productId;

    private String spuCode;

    private String name;
}
