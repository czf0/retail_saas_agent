package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ReviewCreateReq;
import com.retail.business.dto.req.ReviewQueryReq;
import com.retail.business.dto.req.ReviewReplyReq;
import com.retail.business.dto.resp.ReviewApproveResp;
import com.retail.business.dto.resp.ReviewCreateResp;
import com.retail.business.dto.resp.ReviewDeleteResp;
import com.retail.business.dto.resp.ReviewListItemResp;
import com.retail.business.dto.resp.ReviewRejectResp;
import com.retail.business.dto.resp.ReviewReplyResp;
import com.retail.business.dto.resp.ReviewResp;
import com.retail.business.dto.resp.ReviewStatsResp;
import com.retail.business.entity.ProductReview;

/**
 * 商品评价服务.
 */
public interface ProductReviewService extends IService<ProductReview> {

    /** 创建评价:校验商品存在,状态默认 pending. */
    ReviewCreateResp createReview(ReviewCreateReq req);

    /** 分页查询评价,支持 productId / rating / status 过滤. */
    PageResp<ReviewListItemResp> listReviews(Long productId, Integer rating, Integer status);

    /**
     * 分页查询评价(业务语义过滤).
     * <p>
     * 在原有基础上额外支持商品名(反查商品ID集合再 IN),评价内容关键词,评价时间范围过滤.
     * product_review 表无会员字段,故不支持 memberName 过滤.
     */
    PageResp<ReviewListItemResp> listReviews(ReviewQueryReq req);

    /** 评价详情. */
    ReviewResp getReview(Long reviewId);

    /** 回复评价:设置回复内容与回复时间. */
    ReviewReplyResp replyReview(Long reviewId, ReviewReplyReq req);

    /**
     * 审核通过评价: 状态 pending → approved.
     * <p>前置条件: 评价必须存在且处于 pending 状态, 否则抛 BizException.
     * <p>副作用: 审核通过后评价对外可见, 计入商品评分统计 (avgRating / positiveRate).
     */
    ReviewApproveResp approveReview(Long reviewId);

    /**
     * 审核拒绝评价: 状态 pending → rejected.
     * <p>前置条件: 评价必须存在且处于 pending 状态, 否则抛 BizException.
     * <p>副作用: 拒绝后评价对外隐藏, 不计入商品评分统计.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    ReviewRejectResp rejectReview(Long reviewId);

    /** 逻辑删除评价. */
    ReviewDeleteResp deleteReview(Long reviewId);

    /** 评价统计:total / avgRating / positiveRate / approvedCount / pendingCount. */
    ReviewStatsResp getReviewStats(Long productId);
}
