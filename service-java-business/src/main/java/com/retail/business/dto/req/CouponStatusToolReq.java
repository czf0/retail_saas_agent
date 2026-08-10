package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 优惠券模板启用/停用工具(coupon:status, Agent 自然语言解析后调用).
 * <p>对应 Service 方法: CouponTemplateService.switchStatus(req); Agent 触发时 Service 层触发 HITL(destructive=true, 铁律 19).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class CouponStatusToolReq {

    /** 优惠券模板 id, 对应 coupon_template.id; 可选, 优先使用; 否则用 name 反查. */
    private Long couponId;

    private String name;
}
