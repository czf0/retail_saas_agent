package com.retail.business.agent;

import cn.hutool.core.util.StrUtil;
import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.req.ReportToolReq;
import com.retail.business.dto.resp.report.AovAnalysisResp;
import com.retail.business.dto.resp.report.CategorySalesResp;
import com.retail.business.dto.resp.report.CouponRedeemResp;
import com.retail.business.dto.resp.report.CouponRoiResp;
import com.retail.business.dto.resp.report.FinanceSummaryResp;
import com.retail.business.dto.resp.report.InventoryTurnoverResp;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.dto.resp.report.MemberLevelDistResp;
import com.retail.business.dto.resp.report.MemberRfmResp;
import com.retail.business.dto.resp.report.OrderFunnelResp;
import com.retail.business.dto.resp.report.PayTypeDistResp;
import com.retail.business.dto.resp.report.ProductSalesRankResp;
import com.retail.business.dto.resp.report.RefundAnalysisResp;
import com.retail.business.dto.resp.report.SalesSummaryResp;
import com.retail.business.dto.resp.report.SalesTrendResp;
import com.retail.business.dto.resp.report.SlowMovingResp;
import com.retail.business.dto.resp.report.StockAlertResp;
import com.retail.business.dto.resp.report.StockFundResp;
import com.retail.business.dto.resp.report.StoreSalesCompareResp;
import com.retail.business.service.CouponReportService;
import com.retail.business.service.FinanceReportService;
import com.retail.business.service.InventoryReportService;
import com.retail.business.service.MemberReportService;
import com.retail.business.service.OrderReportService;
import com.retail.business.service.SalesReportService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.exception.ParamException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 经营报表 Agent 工具服务 (business="report").
 * <p>
 * 将报表中心 19 个 HTTP 端点逐一封装为只读报表工具,复用 6 个 {@code *ReportService},
 * 零新增业务逻辑.入参统一 {@link ReportToolReq}(时间范围 + 门店/分类/商品维度),
 * 工具层转换为业务层 {@link ReportTimeRangeReq} 后透传.
 * <p>
 * 权限对齐 ReportController 的 @SaCheckPermission:
 * sales / inventory / order / member / coupon / finance 六类权限标识.
 * 报表均为只读聚合,非破坏性操作,不触发 HITL.
 */
@AgentToolService(business = "report")
public class ReportAgentToolService {

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;
    private final OrderReportService orderReportService;
    private final MemberReportService memberReportService;
    private final CouponReportService couponReportService;
    private final FinanceReportService financeReportService;

    /** 单构造器自动注入全部 ReportService 依赖 */
    public ReportAgentToolService(SalesReportService salesReportService,
                                  InventoryReportService inventoryReportService,
                                  OrderReportService orderReportService,
                                  MemberReportService memberReportService,
                                  CouponReportService couponReportService,
                                  FinanceReportService financeReportService) {
        this.salesReportService = salesReportService;
        this.inventoryReportService = inventoryReportService;
        this.orderReportService = orderReportService;
        this.memberReportService = memberReportService;
        this.couponReportService = couponReportService;
        this.financeReportService = financeReportService;
    }

    /**
     * 工具入参转换为业务层报表参数.
     * <p>
     * 时间字段为业务人员直觉的 yyyy-MM-dd 字符串,转换为当天 00:00:00 / 23:59:59 的 LocalDateTime.
     */
    private ReportTimeRangeReq toTimeRange(ReportToolReq req) {
        ReportTimeRangeReq r = new ReportTimeRangeReq();
        try {
            if (StrUtil.isNotBlank(req.getStartDate())) {
                r.setStartDate(LocalDate.parse(req.getStartDate()).atStartOfDay());
            }
            if (StrUtil.isNotBlank(req.getEndDate())) {
                r.setEndDate(LocalDate.parse(req.getEndDate()).atTime(LocalTime.MAX));
            }
        } catch (Exception e) {
            throw new ParamException("日期格式应为 yyyy-MM-dd，请检查 startDate/endDate");
        }
        r.setStoreId(req.getStoreId());
        r.setCategoryId(req.getCategoryId());
        r.setProductId(req.getProductId());
        return r;
    }

    // ===================== 销售报表 =====================

    /** 销售汇总:总GMV / 订单数 / 客单价 / 退款率 / 优惠金额 */
    @AgentTool(
        operation = "sales_summary",
        description = "查询销售汇总。返回总销售额(GMV)、订单数、客单价、退款率、优惠金额。支持时间范围、门店、分类、商品维度过滤。用于回答'今天卖了多少''这个月业绩怎么样'等问题。",
        requiredPermission = "business:report:sales",
        outputHint = "返回销售汇总，包含总销售额、订单数、客单价、退款率、优惠金额。展示为结构化文本，金额保留 2 位小数。"
    )
    public SalesSummaryResp salesSummary(ReportToolReq req) {
        return salesReportService.getSummary(toTimeRange(req));
    }

    /** 商品销售排行 */
    @AgentTool(
        operation = "product_rank",
        description = "查询商品销售排行。按商品维度聚合销量与销售额并排序。支持时间范围、门店、分类过滤。用于回答'哪些商品卖得最好''销量Top10'等问题。",
        requiredPermission = "business:report:sales",
        outputHint = "返回商品销售排行列表，包含商品名、销量、销售额、占比。展示为 markdown 表格，按销售额降序。"
    )
    public List<ProductSalesRankResp> productRank(ReportToolReq req) {
        return salesReportService.getProductRank(toTimeRange(req));
    }

    /** 分类销售占比 */
    @AgentTool(
        operation = "category_sales",
        description = "查询分类销售占比。按商品分类维度聚合销售额与占比。支持时间范围、门店过滤。用于回答'哪个分类卖得多''分类销售占比'等问题。",
        requiredPermission = "business:report:sales",
        outputHint = "返回分类销售列表，包含分类名、销售额、占比。展示为 markdown 表格，按占比降序。"
    )
    public List<CategorySalesResp> categorySales(ReportToolReq req) {
        return salesReportService.getCategorySales(toTimeRange(req));
    }

    /** 门店销售对比 */
    @AgentTool(
        operation = "store_compare",
        description = "查询门店销售对比。按门店维度聚合销售额、订单数、客单价。支持时间范围过滤。用于回答'各门店业绩对比''哪个店卖得好'等问题。",
        requiredPermission = "business:report:sales",
        outputHint = "返回门店销售对比列表，包含门店名、销售额、订单数、客单价。展示为 markdown 表格。"
    )
    public List<StoreSalesCompareResp> storeCompare(ReportToolReq req) {
        return salesReportService.getStoreCompare(toTimeRange(req));
    }

    /** 销售趋势 */
    @AgentTool(
        operation = "sales_trend",
        description = "查询销售趋势。按日聚合销售金额与订单数，返回趋势序列。支持时间范围、门店、分类过滤。用于回答'本月销售走势''最近30天销售'等问题。",
        requiredPermission = "business:report:sales",
        outputHint = "返回销售趋势列表，包含日期、销售额、订单数。展示为文本序列或表格。"
    )
    public List<SalesTrendResp> salesTrend(ReportToolReq req) {
        return salesReportService.getSalesTrend(toTimeRange(req));
    }

    // ===================== 库存报表 =====================

    /** 库存周转率 */
    @AgentTool(
        operation = "inventory_turnover",
        description = "查询库存周转率。按商品维度计算出库成本与平均库存价值的比值。支持时间范围、门店、分类过滤。用于回答'哪些商品库存周转慢''库存周转情况'等问题。",
        requiredPermission = "business:report:inventory",
        outputHint = "返回库存周转率列表，包含商品、出库成本、平均库存价值、周转率。展示为 markdown 表格。"
    )
    public List<InventoryTurnoverResp> inventoryTurnover(ReportToolReq req) {
        return inventoryReportService.getTurnover(toTimeRange(req));
    }

    /** 滞销商品 */
    @AgentTool(
        operation = "slow_moving",
        description = "查询滞销商品。返回指定时间范围内无出库动销的商品。支持时间范围、门店、分类过滤。用于回答'哪些商品滞销''压货的有哪些'等问题。",
        requiredPermission = "business:report:inventory",
        outputHint = "返回滞销商品列表，包含商品、滞销天数、当前库存。展示为 markdown 表格。"
    )
    public List<SlowMovingResp> slowMoving(ReportToolReq req) {
        return inventoryReportService.getSlowMoving(toTimeRange(req));
    }

    /** 缺货预警 */
    @AgentTool(
        operation = "stock_alerts",
        description = "查询缺货预警。返回可用库存低于安全库存阈值的商品。支持门店、分类过滤。用于回答'哪些商品缺货''库存不足预警'等问题。",
        requiredPermission = "business:report:inventory",
        outputHint = "返回缺货预警列表，包含商品、可用库存、安全库存。展示为 markdown 表格。"
    )
    public List<StockAlertResp> stockAlerts(ReportToolReq req) {
        return inventoryReportService.getStockAlerts(toTimeRange(req));
    }

    /** 库存资金占用 */
    @AgentTool(
        operation = "stock_fund",
        description = "查询库存资金占用。按商品维度统计库存价值及占比。支持门店、分类过滤。用于回答'仓库压了多少资金''库存资金占用'等问题。",
        requiredPermission = "business:report:inventory",
        outputHint = "返回库存资金占用列表，包含商品、库存价值、占比。展示为 markdown 表格。"
    )
    public List<StockFundResp> stockFund(ReportToolReq req) {
        return inventoryReportService.getStockFund(toTimeRange(req));
    }

    // ===================== 订单报表 =====================

    /** 订单转化漏斗 */
    @AgentTool(
        operation = "order_funnel",
        description = "查询订单转化漏斗。按订单状态阶段统计订单数与转化率。支持时间范围、门店过滤。用于回答'订单转化率怎么样''各阶段订单数'等问题。",
        requiredPermission = "business:report:order",
        outputHint = "返回订单漏斗列表，包含阶段、订单数、转化率。展示为 markdown 表格。"
    )
    public List<OrderFunnelResp> orderFunnel(ReportToolReq req) {
        return orderReportService.getOrderFunnel(toTimeRange(req));
    }

    /** 退款分析 */
    @AgentTool(
        operation = "refund_analysis",
        description = "查询退款分析。返回退款总金额、笔数、全额与部分退款占比。支持时间范围、门店过滤。用于回答'最近退款多不多''退款率怎么样'等问题。",
        requiredPermission = "business:report:order",
        outputHint = "返回退款分析，包含退款总额、退款笔数、全额/部分退款占比。展示为结构化文本。"
    )
    public RefundAnalysisResp refundAnalysis(ReportToolReq req) {
        return orderReportService.getRefundAnalysis(toTimeRange(req));
    }

    /** 客单价分析 */
    @AgentTool(
        operation = "aov",
        description = "查询客单价分析。返回 GMV、订单数、客单价、退款率、平均订单商品数。支持时间范围、门店过滤。用于回答'客单价多少''平均每单多少钱'等问题。",
        requiredPermission = "business:report:order",
        outputHint = "返回客单价分析，包含GMV、订单数、客单价、退款率、平均商品数。展示为结构化文本。"
    )
    public AovAnalysisResp aovAnalysis(ReportToolReq req) {
        return orderReportService.getAovAnalysis(toTimeRange(req));
    }

    // ===================== 会员报表 =====================

    /** RFM 分群 */
    @AgentTool(
        operation = "member_rfm",
        description = "查询会员RFM分群。基于最近购买时间、频次、金额将会员分为8类。支持时间范围过滤。用于回答'我的会员是什么样的''有哪些高价值会员'等问题。",
        requiredPermission = "business:report:member",
        outputHint = "返回RFM分群列表，包含分群名称、会员数、占比。展示为 markdown 表格。"
    )
    public List<MemberRfmResp> memberRfm(ReportToolReq req) {
        return memberReportService.getRfm(toTimeRange(req));
    }

    /** 会员等级分布 */
    @AgentTool(
        operation = "member_level_dist",
        description = "查询会员等级分布。按普通/银卡/金卡/钻石统计人数与占比。用于回答'金卡会员有多少''会员等级分布'等问题。",
        requiredPermission = "business:report:member",
        outputHint = "返回会员等级分布列表，包含等级、人数、占比。展示为 markdown 表格。"
    )
    public List<MemberLevelDistResp> memberLevelDist(ReportToolReq req) {
        return memberReportService.getLevelDist(toTimeRange(req));
    }

    /** 会员增长趋势 */
    @AgentTool(
        operation = "member_growth",
        description = "查询会员增长趋势。按日统计新增会员数与活跃会员数。支持时间范围过滤。用于回答'会员增长怎么样''最近新增多少会员'等问题。",
        requiredPermission = "business:report:member",
        outputHint = "返回会员增长趋势列表，包含日期、新增会员数、活跃会员数。展示为文本序列或表格。"
    )
    public List<MemberGrowthResp> memberGrowth(ReportToolReq req) {
        return memberReportService.getGrowth(toTimeRange(req));
    }

    // ===================== 营销报表 =====================

    /** 优惠券核销率 */
    @AgentTool(
        operation = "coupon_redeem",
        description = "查询优惠券核销率。按券模板统计发放数、已使用数及核销率。支持时间范围过滤。用于回答'发的券核销了多少''券核销率'等问题。",
        requiredPermission = "business:report:coupon",
        outputHint = "返回优惠券核销率列表，包含券名、发放数、已用数、核销率。展示为 markdown 表格。"
    )
    public List<CouponRedeemResp> couponRedeem(ReportToolReq req) {
        return couponReportService.getRedeemRate(toTimeRange(req));
    }

    /** 营销 ROI */
    @AgentTool(
        operation = "coupon_roi",
        description = "查询营销ROI。按券模板统计折扣金额与带来销售额，计算投入产出比。支持时间范围过滤。用于回答'发的券值不值''营销ROI'等问题。",
        requiredPermission = "business:report:coupon",
        outputHint = "返回营销ROI列表，包含券名、折扣金额、带来销售额、ROI。展示为 markdown 表格。"
    )
    public List<CouponRoiResp> couponRoi(ReportToolReq req) {
        return couponReportService.getRoi(toTimeRange(req));
    }

    // ===================== 财务报表 =====================

    /** 财务汇总 */
    @AgentTool(
        operation = "finance_summary",
        description = "查询财务汇总。返回总收入、退款金额、净收入、优惠金额、订单数。支持时间范围、门店过滤。用于回答'赚了多少''财务汇总'等问题。",
        requiredPermission = "business:report:finance",
        outputHint = "返回财务汇总，包含总收入、退款金额、净收入、优惠金额、订单数。展示为结构化文本，金额保留 2 位小数。"
    )
    public FinanceSummaryResp financeSummary(ReportToolReq req) {
        return financeReportService.getFinanceSummary(toTimeRange(req));
    }

    /** 支付方式分布 */
    @AgentTool(
        operation = "pay_type_dist",
        description = "查询支付方式分布。按微信/支付宝/余额/现金聚合金额与占比。支持时间范围、门店过滤。用于回答'用户都用什么支付''支付方式分布'等问题。",
        requiredPermission = "business:report:finance",
        outputHint = "返回支付方式分布列表，包含支付方式、金额、占比。展示为 markdown 表格。"
    )
    public List<PayTypeDistResp> payTypeDist(ReportToolReq req) {
        return financeReportService.getPayTypeDist(toTimeRange(req));
    }
}
