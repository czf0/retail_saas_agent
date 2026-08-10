package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 拒审/隐藏评价工具(review:reject, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: ReviewService.reject(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class ReviewRejectToolReq {

    /** 目标评价 id, 对应 review.id. */
    private Long reviewId;

    private String reason;
}
