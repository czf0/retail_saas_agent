package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ReviewCreateReq;
import com.retail.business.dto.req.ReviewReplyReq;
import com.retail.business.dto.resp.ReviewApproveResp;
import com.retail.business.dto.resp.ReviewCreateResp;
import com.retail.business.dto.resp.ReviewDeleteResp;
import com.retail.business.dto.resp.ReviewListItemResp;
import com.retail.business.dto.resp.ReviewRejectResp;
import com.retail.business.dto.resp.ReviewReplyResp;
import com.retail.business.dto.resp.ReviewResp;
import com.retail.business.dto.resp.ReviewStatsResp;
import com.retail.business.service.ProductReviewService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价管理接口.
 * <p>路由前缀 /api/v1/reviews.product_review 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:review:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>评价生命周期:创建(PENDING 待审核)→ 审核通过(APPROVED 前台展示)/ 拒绝(REJECTED 不展示)→ 逻辑删除.
 * <p>注意:/stats 为字面量路径,须在 /{reviewId} 之前注册以保证优先匹配.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public ProductReviewController(ProductReviewService productReviewService) {
        this.productReviewService = productReviewService;
    }

    /**
     * 创建商品评价(状态默认 PENDING 待审核).
     * <p>评分 1-5 星,评价内容可选图片与文字,关联订单号回溯购买真实性.
     */
    @PostMapping
    @SaCheckPermission("business:review:add")
    public R<ReviewCreateResp> create(@RequestBody ReviewCreateReq req) {
        return R.ok(productReviewService.createReview(req));
    }

    /**
     * 分页查询评价列表(按商品 / 星级 / 状态过滤,敏感查询需权限).
     */
    @GetMapping
    @SaCheckPermission("business:review:query")
    public R<PageResp<ReviewListItemResp>> list(@RequestParam(required = false) Long productId,
                                                 @RequestParam(required = false) Integer rating,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(productReviewService.listReviews(productId, rating, status));
    }

    /**
     * 评价统计(好评率 / 星级分布 / 总条数,敏感查询需权限).
     */
    @GetMapping("/stats")
    @SaCheckPermission("business:review:query")
    public R<ReviewStatsResp> stats(@RequestParam(required = false) Long productId) {
        return R.ok(productReviewService.getReviewStats(productId));
    }

    /**
     * 查询评价详情(含星级 / 评价内容 / 图片 / 商家回复 / 审核状态).
     */
    @GetMapping("/{reviewId:\\d+}")
    @SaCheckPermission("business:review:query")
    public R<ReviewResp> detail(@PathVariable Long reviewId) {
        return R.ok(productReviewService.getReview(reviewId));
    }

    /**
     * 商家回复评价(在评价详情页面展示商家回复内容).
     */
    @PutMapping("/{reviewId:\\d+}/reply")
    @SaCheckPermission("business:review:edit")
    public R<ReviewReplyResp> reply(@PathVariable Long reviewId,
                                    @RequestBody ReviewReplyReq req) {
        return R.ok(productReviewService.replyReview(reviewId, req));
    }

    /**
     * 审核通过评价(PENDING → APPROVED,前台商品详情页展示).
     */
    @PutMapping("/{reviewId:\\d+}/approve")
    @SaCheckPermission("business:review:audit")
    public R<ReviewApproveResp> approve(@PathVariable Long reviewId) {
        return R.ok(productReviewService.approveReview(reviewId));
    }

    /**
     * 审核拒绝评价(PENDING → REJECTED,前台不展示).
     */
    @PutMapping("/{reviewId:\\d+}/reject")
    @SaCheckPermission("business:review:audit")
    public R<ReviewRejectResp> reject(@PathVariable Long reviewId) {
        return R.ok(productReviewService.rejectReview(reviewId));
    }

    /**
     * 删除评价(逻辑删除,BaseServiceImpl 填充 delete_at / delete_by 审计字段).
     */
    @DeleteMapping("/{reviewId:\\d+}")
    @SaCheckPermission("business:review:remove")
    public R<ReviewDeleteResp> delete(@PathVariable Long reviewId) {
        return R.ok(productReviewService.deleteReview(reviewId));
    }
}
