package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品评价统计汇总响应(PDP 评价区头部"好评率/平均分"卡片 + 后台审核概览);按 product_id 聚合总评数 + 平均星级 + 好评率 + 各状态计数.
 * <p>统计口径:仅 deleted = 0 的评价;pending 计数用于运营"待审核"入口红点提示;productId = NULL 时返回租户全局统计.
 */
@Data
public class ReviewStatsResp {
    /** 该商品(或全局)评价总条数 = approved + rejected + pending. */
    private Long total;
    /** 计算字段:平均星级评分 = SUM(rating) / total,四舍五入保留 2 位小数;前端 5 颗星半星渲染. */
    private BigDecimal avgRating;
    /** 计算字段:好评率(百分比)= COUNT(rating>=4) / total * 100,保留 2 位小数;前端展示 "98.5%". */
    private BigDecimal positiveRate;
    /** 已通过审核状态评价条数(APPROVED,前台可见). */
    private Long approvedCount;
    /** 待审核状态评价条数(PENDING,后台审核入口红点用). */
    private Long pendingCount;
}
