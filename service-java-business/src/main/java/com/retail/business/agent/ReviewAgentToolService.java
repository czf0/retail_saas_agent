package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.ReviewActionToolReq;
import com.retail.business.dto.req.ReviewDeleteToolReq;
import com.retail.business.dto.req.ReviewQueryReq;
import com.retail.business.dto.req.ReviewQueryToolReq;
import com.retail.business.dto.req.ReviewRejectToolReq;
import com.retail.business.dto.req.ReviewReplyReq;
import com.retail.business.dto.resp.ReviewApproveResp;
import com.retail.business.dto.resp.ReviewDeleteResp;
import com.retail.business.dto.resp.ReviewListItemResp;
import com.retail.business.dto.resp.ReviewRejectResp;
import com.retail.business.dto.resp.ReviewReplyResp;
import com.retail.business.dto.resp.ReviewStatsResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.service.ProductReviewService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;

import java.util.List;

/**
 * 商品评价 Agent 工具服务 (business="review").
 * <p>
 * 聚合评价域的工具方法, 复用 {@link ProductReviewService} 现有业务逻辑:
 * <ul>
 *   <li>{@code review:query}  — 分页查询评价列表 (只读, 多条件过滤);</li>
 *   <li>{@code review:stats}  — 查询评价统计 (只读, 总数/均分/好评率);</li>
 *   <li>{@code review:reply}  — 回复评价 (破坏性, HITL 审批);</li>
 *   <li>{@code review:approve}— 审核通过评价 (破坏性, HITL 审批);</li>
 *   <li>{@code review:reject} — 拒审/隐藏评价 (破坏性, HITL 审批);</li>
 *   <li>{@code review:delete} — 删除评价 (软删, 破坏性, HITL 审批).</li>
 * </ul>
 * <p>
 * 权限说明: ProductReviewController 无 @SaCheckPermission, 依赖多租户隔离即可,
 * 因此只读/回复/审核工具 requiredPermission 显式设为空串 "" (不自动推导, 无权限要求);
 * 拒审/删除属敏感管理操作, 配置 requiredPermission = business:review:audit / business:review:delete.
 */
@AgentToolService(business = "review")
public class ReviewAgentToolService {

    private final ProductReviewService productReviewService;
    private final ProductInfoMapper productInfoMapper;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public ReviewAgentToolService(ProductReviewService productReviewService, ProductInfoMapper productInfoMapper) {
        this.productReviewService = productReviewService;
        this.productInfoMapper = productInfoMapper;
    }

    /**
     * 按商品名称定位商品ID:优先使用传入的 productId;否则按 productName 精确反查.
     * <p>
     * 业务人员通常只掌握商品名称,不掌握内部商品ID,故提供按名称定位的入口.
     * 反查时要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param productId   商品ID(可空)
     * @param productName 商品名称(可空)
     * @return 解析后的商品ID
     */
    private Long resolveProductId(Long productId, String productName) {
        if (productId != null) {
            return productId;
        }
        if (StrUtil.isBlank(productName)) {
            throw new ParamException("请提供商品ID或商品名称");
        }
        List<ProductInfo> matches = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                        .select(ProductInfo::getId)
                        .eq(ProductInfo::getName, productName));
        if (matches == null || matches.size() != 1) {
            throw new ParamException("未找到唯一匹配的商品，请提供更精确的商品名称");
        }
        return matches.get(0).getId();
    }

    /**
     * 分页查询评价列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link ProductReviewService#listReviews}, 对齐 ProductReviewController.list (无 @SaCheckPermission).
     *
     * @param req 查询条件 (productId / rating / status + 分页)
     * @return 评价列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询商品评价列表。支持按商品ID或商品名称、评分(1-5)、状态(待审核/已通过/已拒绝)、评价内容关键词、评价时间范围过滤。可分页。用于回答'白色T恤的评价''差评有哪些''最近7天的差评'等问题。",
        requiredPermission = "",
        outputHint = "返回评价列表，包含评价ID、商品名称、会员、评分、内容、状态、评价时间。展示为 markdown 表格，评分用星号标注。"
    )
    public PageResp<ReviewListItemResp> query(ReviewQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // 同名字段复制到业务层 ReviewQueryReq(分页参数不进入业务 Req)
            ReviewQueryReq queryReq = new ReviewQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return productReviewService.listReviews(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询评价统计 (只读).
     * <p>
     * 复用 {@link ProductReviewService#getReviewStats}, 对齐 ProductReviewController.stats (无 @SaCheckPermission).
     * 返回总数/平均分/好评率/已通过数/待审核数.
     *
     * @param req 查询条件 (productId)
     * @return 评价统计
     */
    @AgentTool(
        operation = "stats",
        description = "查询商品评价统计。返回总评价数、平均评分、好评率、已通过数、待审核数。支持按商品ID或商品名称定位商品。用于回答'白色T恤的评价情况''评分怎么样'等问题。",
        requiredPermission = "",
        outputHint = "返回评价统计，包含总评价数、平均评分、好评率、已通过数、待审核数。展示为结构化文本，平均评分保留 1 位小数。"
    )
    public ReviewStatsResp stats(ReviewActionToolReq req) {
        Long productId = resolveProductId(req.getProductId(), req.getProductName());
        return productReviewService.getReviewStats(productId);
    }

    /**
     * 回复评价 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductReviewService#replyReview}, 对齐 ProductReviewController.reply (无 @SaCheckPermission).
     * 设置回复内容与回复时间.
     *
     * @param req 回复请求 (reviewId / content)
     * @return 回复结果
     */
    @AgentTool(
        operation = "reply",
        description = "回复商品评价。需要评价ID和回复内容。回复后评价会显示商家回复。此操作会公开回复内容，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "",
        outputHint = "返回回复结果，包含评价ID、回复内容、回复时间。展示为文本，提示用户回复已发布。"
    )
    public ReviewReplyResp reply(ReviewActionToolReq req) {
        ReviewReplyReq replyReq = new ReviewReplyReq();
        replyReq.setReplyContent(req.getContent());
        return productReviewService.replyReview(req.getReviewId(), replyReq);
    }

    /**
     * 审核通过评价 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductReviewService#approveReview}, 对齐 ProductReviewController.approve (无 @SaCheckPermission).
     * 将评价状态从 pending 改为 approved, 通过后评价对外可见.
     *
     * @param req 操作请求 (reviewId)
     * @return 审核结果
     */
    @AgentTool(
        operation = "approve",
        description = "审核通过商品评价。将待审核评价状态改为已通过，通过后评价对外可见。需要评价ID。此操作会改变评价可见性，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "",
        outputHint = "返回审核结果，包含评价ID、审核状态。展示为文本，提示用户评价已审核通过。"
    )
    public ReviewApproveResp approve(ReviewActionToolReq req) {
        return productReviewService.approveReview(req.getReviewId());
    }

    /**
     * 拒审/隐藏评价 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductReviewService#rejectReview}, 将评价状态置为已拒绝 (REJECTED),
     * 前台不再展示, 用于屏蔽恶意/违规差评.
     *
     * @param req 拒审请求 (reviewId + 可选 reason)
     * @return 拒审结果
     */
    @AgentTool(
        operation = "reject",
        description = "拒审/隐藏商品评价。将评价状态置为已拒绝，前台不再展示，用于屏蔽恶意或违规差评。需要评价ID，可附拒审原因。"
                + "此操作会改变评价可见性且不可直接恢复，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:review:audit",
        outputHint = "返回拒审结果，包含评价ID、拒审状态。展示为文本，提示用户该评价已拒审/隐藏。"
    )
    public ReviewRejectResp reject(ReviewRejectToolReq req) {
        return productReviewService.rejectReview(req.getReviewId());
    }

    /**
     * 删除评价 (软删, 破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductReviewService#deleteReview}, 逻辑删除评价记录.
     *
     * @param req 删除请求 (reviewId)
     * @return 删除结果
     */
    @AgentTool(
        operation = "delete",
        description = "删除商品评价（软删除，记录仍在库里但不再出现在评价列表）。按评价ID定位。"
                + "此操作不可撤销，需要用户确认后才可执行；如果只想屏蔽可以先用 reject 工具。",
        destructive = true,
        requiredPermission = "business:review:delete",
        outputHint = "返回删除结果，包含评价ID、删除状态。展示为文本，提示用户该评价已删除。"
    )
    public ReviewDeleteResp delete(ReviewDeleteToolReq req) {
        return productReviewService.deleteReview(req.getReviewId());
    }
}
