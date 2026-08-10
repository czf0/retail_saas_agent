package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 促销活动状态变更工具(promotion:status, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: PromotionService.switchStatus(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class PromotionStatusToolReq {

    /** 促销活动 id, 对应 promotion.id; 可选; 否则用 name 反查. */
    private Long promotionId;

    private String name;
}
