package com.retail.business.dto.req;

import lombok.Data;

/**
 * 促销活动分页查询请求(运营后台营销管理 -> 活动列表筛选).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 * <p>供 HTTP 接口与 promotion:query 工具共同复用.
 */
@Data
public class PromotionQueryReq {

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
}
