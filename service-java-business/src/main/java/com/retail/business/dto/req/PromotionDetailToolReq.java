package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 促销活动详情/商品活动查询工具(promotion:detail, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(单条详情/关联查询).
 * <p>多维定位: 支持 promotionId/name/productId/productName 多维自然语言解析(铁律 20).
 */
@Data
public class PromotionDetailToolReq {

    /** 促销活动 id, 对应 promotion.id; 查询活动详情时使用, 可选; 否则用 name 反查. */
    private Long promotionId;

    private String name;

    /** 目标商品 id, 对应 product.id; 查询商品参与的活动时使用, 可选; 否则用 productName 反查. */
    private Long productId;

    private String productName;
}
