package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销活动创建请求(运营后台营销管理 -> 新增促销活动).
 * <p>对应 Controller 路由: POST /api/v1/promotions; status 字段 Service 层按时间区间自动推断(铁律 6),
 * CreateReq 不承载 status.
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class PromotionCreateReq {
    private String name;
    /** PromotionType 枚举 code: 1=COUPON 优惠券 2=DISCOUNT 折扣 3=FLASH_SALE 限时秒杀. */
    private Integer type;
    /** TargetType 枚举 code: 1=PRODUCT 指定商品 2=CATEGORY 指定分类 3=ALL 全场; 决定 targetIds 语义. */
    private Integer targetType;
    /** 适用范围 id 列表; targetType=PRODUCT 时为 product.id 列表, targetType=CATEGORY 时为 product_category.id 列表, targetType=ALL 时为空. */
    private List<String> targetIds;
    /** 活动开始时间(含, Asia/Shanghai); 晚于当前时间, 必须早于 endTime. */
    private LocalDateTime startTime;
    /** 活动结束时间(含, Asia/Shanghai); 必须晚于 startTime. */
    private LocalDateTime endTime;
    /** 活动规则参数(JSON Map); type=DISCOUNT 时含 discountPercent, type=FLASH_SALE 时含 flashPrice 等, Service 层按 type 解析校验. */
    private Map<String, Object> rules;
}
