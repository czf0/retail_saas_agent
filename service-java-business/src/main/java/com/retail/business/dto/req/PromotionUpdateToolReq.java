package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 工具专用入参: 促销活动更新工具(promotion:update, Agent 自然语言解析后调用).
 * <p>对应 Controller 路由: PUT /api/v1/promotions/{id:\\d+}; {id} 由 promotionId/name 定位后映射.
 * <p>幂等: 以定位到的 {id} 为主键, 多次提交最终一致(最后一次覆盖).
 * <p>分页: 本 ToolReq 无需承载 page/pageSize 字段(非分页接口).
 */
@Data
public class PromotionUpdateToolReq {

    /** 促销活动 id, 对应 promotion.id; 定位用, 可选; 否则用 name 反查. */
    private Long promotionId;

    private String name;

    private String newName;

    /** PromotionStatus 枚举 code: 1=PENDING 未开始 2=ACTIVE 进行中 3=EXPIRED 已结束. */
    private Integer status;

    /** 活动开始时间(含, Asia/Shanghai); 可空, 非空时必须早于 endTime. */
    private LocalDateTime startTime;

    /** 活动结束时间(含, Asia/Shanghai); 可空, 非空时必须晚于 startTime. */
    private LocalDateTime endTime;

    /** 活动规则参数(JSON Map); 如 {"discountRate":0.80} 等, Service 层按 type 解析校验. */
    private Map<String, Object> rules;
}
