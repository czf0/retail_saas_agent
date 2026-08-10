package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.PromotionCreateReq;
import com.retail.business.dto.req.PromotionDetailToolReq;
import com.retail.business.dto.req.PromotionQueryReq;
import com.retail.business.dto.req.PromotionQueryToolReq;
import com.retail.business.dto.req.PromotionStatusToolReq;
import com.retail.business.dto.req.PromotionUpdateReq;
import com.retail.business.dto.req.PromotionUpdateToolReq;
import com.retail.business.dto.resp.ProductPromotionItemResp;
import com.retail.business.dto.resp.PromotionCreateResp;
import com.retail.business.dto.resp.PromotionListItemResp;
import com.retail.business.dto.resp.PromotionResp;
import com.retail.business.dto.resp.PromotionUpdateResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.Promotion;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.PromotionMapper;
import com.retail.business.service.PromotionService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;

import java.util.List;

/**
 * 促销活动 Agent 工具服务 (business="promotion").
 * <p>
 * 聚合促销域的工具方法, 复用 {@link PromotionService} 现有业务逻辑:
 * <ul>
 *   <li>{@code promotion:query}   — 分页查询促销活动列表 (只读, 多条件过滤);</li>
 *   <li>{@code promotion:detail}  — 查询促销活动详情 (只读);</li>
 *   <li>{@code promotion:create}  — 创建促销活动 (破坏性, HITL 审批);</li>
 *   <li>{@code promotion:product} — 查询商品参与的促销活动 (只读);</li>
 *   <li>{@code promotion:enable}  — 启用促销活动 (破坏性, HITL 审批, business:promotion:edit);</li>
 *   <li>{@code promotion:disable} — 停用促销活动 (破坏性, HITL 审批, business:promotion:edit);</li>
 *   <li>{@code promotion:end}     — 提前结束促销活动 (破坏性, HITL 审批, business:promotion:edit);</li>
 *   <li>{@code promotion:update}  — 更新促销活动基础信息/时间 (破坏性, HITL 审批, business:promotion:edit).</li>
 * </ul>
 * <p>
 * 权限说明: PromotionController 无 @SaCheckPermission, 依赖多租户隔离即可,
 * 因此只读工具 (query/detail/create/product) 的 requiredPermission 显式设为空串 "";
 * 而状态变更/更新类破坏性工具 (enable/disable/end/update) 显式要求 business:promotion:edit.
 */
@AgentToolService(business = "promotion")
public class PromotionAgentToolService {

    private final PromotionService promotionService;
    private final PromotionMapper promotionMapper;
    private final ProductInfoMapper productInfoMapper;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public PromotionAgentToolService(PromotionService promotionService, PromotionMapper promotionMapper,
                                     ProductInfoMapper productInfoMapper) {
        this.promotionService = promotionService;
        this.promotionMapper = promotionMapper;
        this.productInfoMapper = productInfoMapper;
    }

    /**
     * 解析促销活动ID:优先使用传入的 promotionId;否则按活动名称反查.
     * <p>
     * 业务人员通常只掌握活动名称,不掌握内部活动ID,故提供按名称定位的入口.
     * 反查要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param promotionId 活动ID(可空)
     * @param name        活动名称(可空)
     * @return 解析后的活动ID
     */
    private Long resolvePromotionId(Long promotionId, String name) {
        if (promotionId != null) {
            return promotionId;
        }
        if (StrUtil.isBlank(name)) {
            throw new ParamException("请提供促销活动ID或活动名称");
        }
        Promotion promotion = promotionMapper.selectOne(
                new LambdaQueryWrapper<Promotion>().eq(Promotion::getName, name));
        if (promotion == null) {
            throw new ParamException("未找到匹配的促销活动，请提供更精确的活动名称");
        }
        return promotion.getId();
    }

    /**
     * 解析商品ID:优先使用传入的 productId;否则按商品名称反查.
     * <p>
     * 业务人员通常只掌握商品名称,不掌握内部商品ID,故提供按名称定位的入口.
     * 反查要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
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
        ProductInfo product = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getName, productName));
        if (product == null) {
            throw new ParamException("未找到匹配的商品，请提供更精确的商品名称");
        }
        return product.getId();
    }

    /**
     * 分页查询促销活动列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link PromotionService#listPromotions}, 对齐 PromotionController.list (无 @SaCheckPermission).
     *
     * @param req 查询条件 (status / targetType / keyword + 分页)
     * @return 促销活动列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询促销活动列表。支持按状态(进行中/未开始/已结束)、目标类型(全品/分类/指定商品)、活动类型(优惠券/折扣/秒杀)、名称关键词、活动起止时间范围过滤。可分页。用于回答'当前促销活动''这个月的满减活动'等问题。",
        requiredPermission = "",
        outputHint = "返回促销活动列表，包含活动名称、类型、目标类型、开始时间、结束时间、状态。展示为 markdown 表格。"
    )
    public PageResp<PromotionListItemResp> query(PromotionQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // 同名字段复制到业务层 PromotionQueryReq(分页参数不进入业务 Req)
            PromotionQueryReq queryReq = new PromotionQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return promotionService.listPromotions(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询促销活动详情 (只读).
     * <p>
     * 复用 {@link PromotionService#getPromotion}, 对齐 PromotionController.detail (无 @SaCheckPermission).
     *
     * @param req 查询条件 (promotionId / name)
     * @return 促销活动详情 (含规则,目标商品)
     */
    @AgentTool(
        operation = "detail",
        description = "查询促销活动详情。支持按促销活动ID或活动名称定位，返回活动完整信息，包括类型、目标范围、时间、规则详情、关联优惠券等。用于回答'促销活动XX的详细信息'。",
        requiredPermission = "",
        outputHint = "返回促销详情，包含名称、类型、目标类型、目标商品列表、开始/结束时间、规则。展示为结构化文本。"
    )
    public PromotionResp detail(PromotionDetailToolReq req) {
        Long promotionId = resolvePromotionId(req.getPromotionId(), req.getName());
        return promotionService.getPromotion(promotionId);
    }

    /**
     * 创建促销活动 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PromotionService#createPromotion}, 对齐 PromotionController.create (无 @SaCheckPermission).
     * 支持优惠券/折扣/限时秒杀三种类型, 目标可为全品/分类/指定商品.
     * 根据当前时间自动推断状态 (未开始/进行中/已结束).
     *
     * @param req 创建请求 (name / type / targetType / targetIds / startTime / endTime / rules)
     * @return 创建结果 (含活动 ID,推断状态)
     */
    @AgentTool(
        operation = "create",
        description = "创建促销活动。需要活动名称、类型(优惠券/折扣/限时秒杀)、目标范围(全品/分类/指定商品)、起止时间、规则。系统根据当前时间自动推断活动状态。此操作会创建促销活动，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "",
        outputHint = "返回创建结果，包含活动ID、名称、类型、状态。展示为文本，提示用户促销活动已创建成功。"
    )
    public PromotionCreateResp create(PromotionCreateReq req) {
        return promotionService.createPromotion(req);
    }

    /**
     * 查询商品参与的促销活动 (只读, 仅返回 active 状态).
     * <p>
     * 复用 {@link PromotionService#getProductPromotions}, 对齐 PromotionController.productPromotions (无 @SaCheckPermission).
     *
     * @param req 查询条件 (productId / productName)
     * @return 商品参与的促销活动列表
     */
    @AgentTool(
        operation = "product",
        description = "查询商品参与的促销活动。支持按商品ID或商品名称定位商品，返回该商品当前正在进行的所有促销活动。用于回答'商品XX有什么优惠''这个商品在打折吗'等问题。",
        requiredPermission = "",
        outputHint = "返回促销活动列表，包含活动名称、类型、规则、起止时间。展示为 markdown 表格。"
    )
    public List<ProductPromotionItemResp> product(PromotionDetailToolReq req) {
        Long productId = resolveProductId(req.getProductId(), req.getProductName());
        return promotionService.getProductPromotions(productId);
    }

    /**
     * 启用促销活动 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PromotionService#enablePromotion}, 将活动状态置为进行中(ACTIVE).
     * 支持按活动ID或活动名称定位.
     *
     * @param req 定位条件 (promotionId / name)
     * @return 启用结果 (成功标志 / 消息 / 更新行数)
     */
    @AgentTool(
        operation = "enable",
        description = "启用促销活动。支持按活动ID或活动名称定位，将活动状态置为进行中即开始生效。用于回答'把XX活动启用''这个活动开始上线'等场景。此操作会启用促销活动，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:promotion:edit",
        outputHint = "返回启用结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已启用。"
    )
    public PromotionUpdateResp enable(PromotionStatusToolReq req) {
        Long promotionId = resolvePromotionId(req.getPromotionId(), req.getName());
        return promotionService.enablePromotion(promotionId);
    }

    /**
     * 停用促销活动 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PromotionService#disablePromotion}, 将活动状态置为未开始(PENDING),
     * 使活动暂停不再进行.支持按活动ID或活动名称定位.
     *
     * @param req 定位条件 (promotionId / name)
     * @return 停用结果 (成功标志 / 消息 / 更新行数)
     */
    @AgentTool(
        operation = "disable",
        description = "停用促销活动。支持按活动ID或活动名称定位，将活动状态置为未开始，使活动暂停不再进行。用于回答'这个活动先停用''把XX活动暂停'等场景。此操作会停用促销活动，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:promotion:edit",
        outputHint = "返回停用结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已停用。"
    )
    public PromotionUpdateResp disable(PromotionStatusToolReq req) {
        Long promotionId = resolvePromotionId(req.getPromotionId(), req.getName());
        return promotionService.disablePromotion(promotionId);
    }

    /**
     * 提前结束促销活动 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PromotionService#endPromotion}, 将活动状态置为已结束(EXPIRED),
     * 并将结束时间置为当前时间,活动立即失效.支持按活动ID或活动名称定位.
     *
     * @param req 定位条件 (promotionId / name)
     * @return 结束结果 (成功标志 / 消息 / 更新行数)
     */
    @AgentTool(
        operation = "end",
        description = "提前结束促销活动。支持按活动ID或活动名称定位，将活动状态置为已结束并立即失效，若活动原定时间未到也会提前终止。用于回答'活动结束了帮我结束它''提前终止XX活动'等场景。此操作会结束促销活动，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:promotion:edit",
        outputHint = "返回结束结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已提前结束。"
    )
    public PromotionUpdateResp end(PromotionStatusToolReq req) {
        Long promotionId = resolvePromotionId(req.getPromotionId(), req.getName());
        return promotionService.endPromotion(promotionId);
    }

    /**
     * 更新促销活动基础信息/时间 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PromotionService#updatePromotion}, 支持部分更新:
     * 活动名称,状态,开始时间,结束时间,活动规则(如折扣规则).
     * 支持按活动ID或活动名称定位.
     *
     * @param req 定位条件 + 可改字段 (promotionId / name + newName / status / startTime / endTime / rules)
     * @return 更新结果 (成功标志 / 消息 / 更新行数)
     */
    @AgentTool(
        operation = "update",
        description = "更新促销活动基础信息或时间。支持按活动ID或活动名称定位，可修改活动名称、状态、开始时间、结束时间、折扣规则等字段（未提供的字段保持不变）。用于回答'把XX活动的结束时间改到月底''修改XX活动名称'等场景。此操作会更新促销活动，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:promotion:edit",
        outputHint = "返回更新结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已更新。"
    )
    public PromotionUpdateResp update(PromotionUpdateToolReq req) {
        Long promotionId = resolvePromotionId(req.getPromotionId(), req.getName());
        // 同名字段复制到业务层 PromotionUpdateReq(name 定位字段映射为 newName 作为新名称)
        PromotionUpdateReq updateReq = new PromotionUpdateReq();
        updateReq.setName(req.getNewName());
        updateReq.setStatus(req.getStatus());
        updateReq.setStartTime(req.getStartTime());
        updateReq.setEndTime(req.getEndTime());
        updateReq.setRules(req.getRules());
        return promotionService.updatePromotion(promotionId, updateReq);
    }
}
