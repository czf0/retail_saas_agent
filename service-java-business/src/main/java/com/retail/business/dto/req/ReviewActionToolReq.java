package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 评价操作工具(review:action, 回复/审核/统计).
 * <p>对应 Service 方法: ReviewService.action(req); 破坏性操作(删除/拒审)时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class ReviewActionToolReq {

    /** 目标评价 id, 对应 review.id. */
    private Long reviewId;

    /** 目标商品 id, 对应 product.id; 查询评价统计时使用. */
    private Long productId;

    private String productName;

    private String content;
}
