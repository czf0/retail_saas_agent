package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 促销活动查询工具(promotion:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>活动定位: 支持 keyword/type/status 多维自然语言解析, 不只依赖 promotionId(铁律 20).
 */
@Data
public class PromotionQueryToolReq {

    /** PromotionStatus 枚举 code: 1=PENDING 未开始 2=ACTIVE 进行中 3=EXPIRED 已结束. */
    private Integer status;

    /** TargetType 枚举 code: 1=PRODUCT 指定商品 2=CATEGORY 指定分类 3=ALL 全场. */
    private Integer targetType;

    private String keyword;

    /** PromotionType 枚举 code: 1=COUPON 优惠券 2=DISCOUNT 折扣 3=FLASH_SALE 限时秒杀. */
    private Integer type;

    /** 活动开始时间范围-起始(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 活动结束时间范围-截止(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
