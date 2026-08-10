package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.req.SalesQueryReq;
import com.retail.business.dto.req.StatsOverviewReq;
import com.retail.business.dto.req.StatsOverviewToolReq;
import com.retail.business.dto.resp.SalesRecordResp;
import com.retail.business.dto.resp.StatsOverviewResp;
import com.retail.business.dto.resp.OrderTrendResp;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.service.MemberReportService;
import com.retail.business.service.StatsService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计分析 Agent 工具服务 (business="stats").
 * <p>
 * 聚合统计域的工具方法, 复用 {@link StatsService} 现有业务逻辑:
 * <ul>
 *   <li>{@code stats:sales}    — 查询销售记录 (只读, 按日期范围);</li>
 *   <li>{@code stats:overview} — 查询经营概览 (只读, 无入参).</li>
 * </ul>
 * <p>
 * 权限说明: StatsController 无 @SaCheckPermission, 依赖多租户隔离即可,
 * 因此 requiredPermission 显式设为空串 "" (不自动推导, 无权限要求).
 */
@AgentToolService(business = "stats")
public class StatsAgentToolService {

    private final StatsService statsService;
    private final MemberReportService memberReportService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public StatsAgentToolService(StatsService statsService, MemberReportService memberReportService) {
        this.statsService = statsService;
        this.memberReportService = memberReportService;
    }

    /**
     * 查询销售记录 (只读, 按日期范围).
     * <p>
     * 复用 {@link StatsService#querySales}, 对齐 StatsController.sales (无 @SaCheckPermission, 依赖租户隔离).
     *
     * @param req 查询条件 (startDate / endDate, 可空=不限)
     * @return 销售记录列表
     */
    @AgentTool(
        operation = "sales",
        description = "查询销售记录。支持按日期范围过滤。返回每日销售额、订单数、客单价等指标。",
        requiredPermission = "",  // StatsController 无 @SaCheckPermission, 显式空串=无权限要求
        outputHint = "返回销售记录列表，包含日期、销售额、订单数、客单价。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public List<SalesRecordResp> sales(SalesQueryReq req) {
        return statsService.querySales(req.getStartDate(), req.getEndDate());
    }

    /**
     * 查询经营概览 (只读, 支持按创建时间范围过滤).
     * <p>
     * 复用 {@link StatsService#overview}, 对齐 StatsController.overview (无 @SaCheckPermission).
     *
     * @param req 过滤条件 (startDate / endDate / storeId, 均可选)
     * @return 经营概览 (商品数,促销数,评价数,会员数)
     */
    @AgentTool(
        operation = "overview",
        description = "查询经营概览。返回商品数、促销数、评价数、会员数等关键指标计数。支持按创建时间范围过滤。用于回答'这个季度经营怎么样'等问题。",
        requiredPermission = "",  // StatsController 无 @SaCheckPermission, 显式空串=无权限要求
        outputHint = "返回经营概览，包含商品数、促销数、评价数、会员数。展示为结构化文本。"
    )
    public StatsOverviewResp overview(StatsOverviewToolReq req) {
        // 同名字段复制到业务层 StatsOverviewReq
        StatsOverviewReq overviewReq = new StatsOverviewReq();
        BeanUtil.copyProperties(req, overviewReq);
        return statsService.overview(overviewReq);
    }

    /**
     * 查询订单趋势 (只读, 按日期范围).
     * <p>
     * 复用 {@link StatsService#queryOrderTrend}, 对齐 StatsController.orderTrend (无 @SaCheckPermission).
     *
     * @param req 查询条件 (startDate / endDate, 可空=不限)
     * @return 订单趋势列表 (按日统计订单数/销售额)
     */
    @AgentTool(
        operation = "order_trend",
        description = "查询订单趋势。返回按日统计的订单数、销售额等指标。支持按日期范围过滤。用于回答'最近7天订单趋势'等问题。",
        requiredPermission = "",  // StatsController 无 @SaCheckPermission, 显式空串=无权限要求
        outputHint = "返回订单趋势列表，包含日期、订单数、销售额。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public List<OrderTrendResp> orderTrend(SalesQueryReq req) {
        return statsService.queryOrderTrend(req.getStartDate(), req.getEndDate());
    }

    /**
     * 查询会员增长趋势 (只读, 按日期范围).
     * <p>
     * 复用 {@link MemberReportService#getGrowth}, 按日统计新增会员数与活跃会员数.
     *
     * @param req 查询条件 (startDate / endDate, 可空=不限)
     * @return 会员增长趋势列表 (按日统计新增/活跃会员数)
     */
    @AgentTool(
        operation = "member_growth",
        description = "查询会员增长趋势。返回按日统计的新增会员数与活跃会员数。支持按日期范围过滤。用于回答'最近一周新增会员'等问题。",
        requiredPermission = "",  // StatsController 无 @SaCheckPermission, 显式空串=无权限要求
        outputHint = "返回会员增长趋势列表，包含日期、新增会员数、活跃会员数。展示为 markdown 表格。"
    )
    public List<MemberGrowthResp> memberGrowth(SalesQueryReq req) {
        // 将字符串日期转换为 ReportTimeRangeReq 的 LocalDateTime(yyyy-MM-dd 解析为当天 00:00:00)
        ReportTimeRangeReq rangeReq = new ReportTimeRangeReq();
        if (req != null && req.getStartDate() != null) {
            rangeReq.setStartDate(LocalDateTime.parse(req.getStartDate() + "T00:00:00"));
        }
        if (req != null && req.getEndDate() != null) {
            rangeReq.setEndDate(LocalDateTime.parse(req.getEndDate() + "T00:00:00"));
        }
        return memberReportService.getGrowth(rangeReq);
    }
}
