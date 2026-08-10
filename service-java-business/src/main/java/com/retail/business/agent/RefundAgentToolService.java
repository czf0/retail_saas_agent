package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.RefundAnalysisToolReq;
import com.retail.business.dto.req.RefundAuditReq;
import com.retail.business.dto.req.RefundAuditToolReq;
import com.retail.business.dto.req.RefundCancelToolReq;
import com.retail.business.dto.req.RefundCreateReq;
import com.retail.business.dto.req.RefundDetailToolReq;
import com.retail.business.dto.req.RefundQueryReq;
import com.retail.business.dto.req.RefundQueryToolReq;
import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.RefundAuditResp;
import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.dto.resp.RefundResp;
import com.retail.business.dto.resp.report.RefundAnalysisResp;
import com.retail.business.entity.OrderRefund;
import com.retail.business.mapper.OrderRefundMapper;
import com.retail.business.service.OrderReportService;
import com.retail.business.service.RefundService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 退款业务 Agent 工具服务 (business="refund").
 * <p>
 * 聚合退款域的工具方法, 复用 {@link RefundService} 现有业务逻辑:
 * <ul>
 *   <li>{@code refund:query}  — 分页查询退款单列表 (只读, 多条件过滤);</li>
 *   <li>{@code refund:detail} — 查询退款单详情 (只读);</li>
 *   <li>{@code refund:create} — 创建退款申请 (破坏性, HITL 审批);</li>
 *   <li>{@code refund:audit}  — 审核退款单 (破坏性, HITL 审批, 触发退款联动);</li>
 *   <li>{@code refund:cancel} — 撤销待审核退款单 (破坏性, HITL 审批);</li>
 *   <li>{@code refund:analysis}— 退款分析 (只读报表).</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:
 * <ul>
 *   <li>query/detail → business:refund:query (对齐 RefundController.list/detail @SaCheckPermission);</li>
 *   <li>create/audit → business:refund:audit (对齐 RefundController.create/audit @SaCheckPermission);</li>
 *   <li>cancel → business:refund:edit;</li>
 *   <li>analysis → business:report:order (对齐 ReportController.refundAnalysis @SaCheckPermission).</li>
 * </ul>
 */
@AgentToolService(business = "refund")
public class RefundAgentToolService {

    private final RefundService refundService;
    private final OrderRefundMapper orderRefundMapper;
    private final OrderReportService orderReportService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public RefundAgentToolService(RefundService refundService, OrderRefundMapper orderRefundMapper,
                                  OrderReportService orderReportService) {
        this.refundService = refundService;
        this.orderRefundMapper = orderRefundMapper;
        this.orderReportService = orderReportService;
    }

    /**
     * 解析退款单ID:优先使用传入的 refundId;否则按 refundNo/orderNo 反查退款单.
     * <p>
     * 业务人员通常只掌握退款单号/原订单号,不掌握内部退款单ID,故提供按业务字段定位的入口.
     * 反查要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param refundId 退款单ID(可空)
     * @param refundNo 退款单号(可空)
     * @param orderNo  原订单号(可空)
     * @return 解析后的退款单ID
     */
    private Long resolveRefundId(Long refundId, String refundNo, String orderNo) {
        if (refundId != null) {
            return refundId;
        }
        if (StrUtil.isBlank(refundNo) && StrUtil.isBlank(orderNo)) {
            throw new ParamException("请提供退款单ID、退款单号或原订单号");
        }
        // 按退款单号/原订单号反查退款单(先查ID再过滤)
        LambdaQueryWrapper<OrderRefund> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(refundNo)) {
            wrapper.eq(OrderRefund::getRefundNo, refundNo);
        } else {
            wrapper.eq(OrderRefund::getOrderNo, orderNo);
        }
        OrderRefund refund = orderRefundMapper.selectOne(wrapper);
        if (refund == null) {
            throw new ParamException("未找到匹配的退款单，请提供更精确的退款单号或原订单号");
        }
        return refund.getId();
    }

    /**
     * 分页查询退款单列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link RefundService#listRefunds}, 对齐 RefundController.list 的 @SaCheckPermission("business:refund:query").
     *
     * @param req 查询条件 (status / orderNo / 日期范围 + 分页)
     * @return 退款单列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询退款单列表。支持按退款状态、订单号、会员姓名/手机号、退款类型(全额/部分)、退款金额区间、时间范围过滤。可分页。用于回答'王五的退款''部分退款''退款金额超100的退款'等问题。",
        requiredPermission = "business:refund:query",
        outputHint = "返回退款单列表，包含退款单号、原订单号、会员姓名、退款金额、退款类型、状态、申请时间。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<RefundListItemResp> query(RefundQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // 同名字段复制到业务层 RefundQueryReq(分页参数不进入业务 Req)
            RefundQueryReq queryReq = new RefundQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return refundService.listRefunds(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询退款单详情 (只读).
     * <p>
     * 复用 {@link RefundService#getRefund}, 对齐 RefundController.detail 的 @SaCheckPermission("business:refund:query").
     *
     * @param req 查询条件 (refundId / refundNo / orderNo)
     * @return 退款单详情
     */
    @AgentTool(
        operation = "detail",
        description = "查询退款单详情。支持按退款单ID、退款单号或原订单号定位，返回退款单完整信息，包括退款金额、退款类型、退款原因、审核状态、关联订单信息。用于回答'退款单XX的详情'。",
        requiredPermission = "business:refund:query",
        outputHint = "返回退款单详情，包含退款单号、原订单号、退款金额、退款类型、退款原因、状态、审核备注。展示为结构化文本，金额保留 2 位小数。"
    )
    public RefundResp detail(RefundDetailToolReq req) {
        Long refundId = resolveRefundId(req.getRefundId(), req.getRefundNo(), req.getOrderNo());
        return refundService.getRefund(refundId);
    }

    /**
     * 创建退款申请 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link RefundService#createRefund}, 对齐 RefundController.create 的 @SaCheckPermission("business:refund:audit").
     * 仅 PAID/SHIPPED/COMPLETED 状态订单可申请退款; 退款金额不超过可退金额.
     * 创建后退款单状态为 PENDING, 订单状态同步标记为 REFUNDING.
     *
     * @param req 创建请求 (orderId / refundType / refundAmount / refundQty / reason)
     * @return 退款单信息 (含退款单 ID)
     */
    @AgentTool(
        operation = "create",
        description = "创建退款申请。需要原订单ID、退款类型、退款金额、退款原因。退款类型为整数code：1全额退款/2部分退款，必须传数字，如全额退传refundType=1。仅已支付/已发货/已完成的订单可退款。创建后退款单进入待审核状态。此操作会创建退款单并标记订单为退款中，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:refund:audit",
        outputHint = "返回退款单信息，包含退款单ID、退款单号、退款金额、状态。展示为文本，提示用户退款申请已创建，等待审核。"
    )
    public RefundResp create(RefundCreateReq req) {
        return refundService.createRefund(req);
    }

    /**
     * 审核退款单 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link RefundService#auditRefund}, 对齐 RefundController.audit 的 @SaCheckPermission("business:refund:audit").
     * 审核通过时事务内执行退款联动: 退券 + 退积分 + 库存回滚 + 订单 refund_amount 累加;
     * 全额退款则订单状态改 REFUNDED. 审核拒绝时订单状态回退.
     *
     * @param req 审核请求 (refundId / refundNo / orderNo / result / remark)
     * @return 审核结果 (含退款联动执行详情)
     */
    @AgentTool(
        operation = "audit",
        description = "审核退款单。支持按退款单ID、退款单号或原订单号定位退款单，需审核结果。审核结果为整数code：2通过/3拒绝，必须传数字，如通过传result=2。审核通过会触发退款联动：退券、退积分、库存回滚、订单金额更新。全额退款则订单状态改为已退款。此操作会执行退款并影响多个模块，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:refund:audit",
        outputHint = "返回审核结果，包含退款单ID、审核结果、退款联动详情（退券数、退积分数、库存回滚数）。展示为结构化文本。"
    )
    public RefundAuditResp audit(RefundAuditToolReq req) {
        Long refundId = resolveRefundId(req.getRefundId(), req.getRefundNo(), req.getOrderNo());
        RefundAuditReq auditReq = new RefundAuditReq();
        auditReq.setResult(req.getResult());
        auditReq.setRemark(req.getRemark());
        return refundService.auditRefund(refundId, auditReq);
    }

    /**
     * 撤销待审核退款单 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link RefundService#cancel}, 仅 PENDING(待审核) 状态的退款单可撤销.
     * 撤销后退款单状态置为 CANCELLED(已撤销),关联订单从退款中(REFUNDING)恢复退款前原状态.
     * 业务人员通常只掌握退款单号(refundNo)而非内部退款单ID,故优先按 refundNo 定位.
     *
     * @param req 撤销请求 (refundId / refundNo)
     * @return 撤销后的退款单信息
     */
    @AgentTool(
        operation = "cancel",
        description = "撤销待审核退款单。支持按退款单ID或退款单号定位退款单。仅待审核(PENDING)状态的退款单可撤销，撤销后退款单进入已撤销状态，关联订单从退款中恢复原状态。用于回答'撤销王五那个填错的退款单''把刚才建错的退款单撤了'等问题。此操作会改变退款单和订单状态，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:refund:edit",
        outputHint = "返回撤销后的退款单信息，包含退款单号、原订单号、退款金额、状态(已撤销)。展示为结构化文本，金额保留 2 位小数。"
    )
    public RefundResp cancel(RefundCancelToolReq req) {
        Long refundId = resolveRefundId(req.getRefundId(), req.getRefundNo(), null);
        return refundService.cancel(refundId);
    }

    /**
     * 退款分析 (只读报表, 非破坏性).
     * <p>
     * 复用 {@link OrderReportService#getRefundAnalysis}, 对齐 ReportController.refundAnalysis 的
     * @SaCheckPermission("business:report:order"). 按时间范围统计退款总金额 / 笔数 / 全额与部分退款占比 / 平均退款金额.
     * orderNo 为可选业务上下文字段,聚合分析不按单过滤.
     *
     * @param req 分析请求 (startDate / endDate / orderNo)
     * @return 退款分析数据
     */
    @AgentTool(
        operation = "analysis",
        description = "查询退款分析。返回退款总金额、退款笔数、全额退款笔数、部分退款笔数、平均退款金额。支持按时间范围过滤。用于回答'最近退款率怎么这么高''这个月退款情况怎么样'等问题。",
        requiredPermission = "business:report:order",
        outputHint = "返回退款分析，包含退款总金额、退款笔数、全额/部分退款笔数、平均退款金额。展示为结构化文本，金额保留 2 位小数。"
    )
    public RefundAnalysisResp analysis(RefundAnalysisToolReq req) {
        ReportTimeRangeReq range = new ReportTimeRangeReq();
        try {
            if (StrUtil.isNotBlank(req.getStartDate())) {
                range.setStartDate(LocalDate.parse(req.getStartDate()).atStartOfDay());
            }
            if (StrUtil.isNotBlank(req.getEndDate())) {
                range.setEndDate(LocalDate.parse(req.getEndDate()).atTime(LocalTime.MAX));
            }
        } catch (Exception e) {
            throw new ParamException("日期格式应为 yyyy-MM-dd，请检查 startDate/endDate");
        }
        return orderReportService.getRefundAnalysis(range);
    }
}
