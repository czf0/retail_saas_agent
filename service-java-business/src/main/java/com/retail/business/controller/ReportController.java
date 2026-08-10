package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.ReportTimeRangeReq;
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
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 经营报表中心统一入口.
 * <p>
 * 路由前缀 /api/v1/reports,整合 6 大类报表 Service,对外暴露 19 个 GET 端点.
 * 所有报表支持 startDate / endDate / storeId / categoryId / productId 参数过滤,
 * 门店白名单表由拦截器自动按当前用户 storeId 隔离,非白名单表在 Service 层手动处理.
 * <p>
 * 权限标识前缀 business:report:*,6 类报表分别对应:
 * sales / inventory / order / member / coupon / finance.
 * <p>
 * 返回结构化 JSON,供 Agent 智能助手直接消费并生成自然语言摘要.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final SalesReportService salesReportService;
    private final InventoryReportService inventoryReportService;
    private final OrderReportService orderReportService;
    private final MemberReportService memberReportService;
    private final CouponReportService couponReportService;
    private final FinanceReportService financeReportService;

    /** 构造注入:单构造器由 Spring 自动注入全部 ReportService 依赖 */
    public ReportController(SalesReportService salesReportService,
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

    // ===================== 销售报表(5 个端点) =====================

    /** 销售汇总:总GMV / 订单数 / 客单价 / 退款率 / 优惠金额 */
    @GetMapping("/sales/summary")
    @SaCheckPermission("business:report:sales")
    public R<SalesSummaryResp> salesSummary(ReportTimeRangeReq req) {
        return R.ok(salesReportService.getSummary(req));
    }

    /** 商品销售排行:按商品维度聚合销量与金额 */
    @GetMapping("/sales/product-rank")
    @SaCheckPermission("business:report:sales")
    public R<List<ProductSalesRankResp>> productRank(ReportTimeRangeReq req) {
        return R.ok(salesReportService.getProductRank(req));
    }

    /** 分类销售占比:按分类维度聚合金额与占比 */
    @GetMapping("/sales/category")
    @SaCheckPermission("business:report:sales")
    public R<List<CategorySalesResp>> categorySales(ReportTimeRangeReq req) {
        return R.ok(salesReportService.getCategorySales(req));
    }

    /** 门店销售对比:按门店维度聚合金额 / 订单数 / 客单价 */
    @GetMapping("/sales/store-compare")
    @SaCheckPermission("business:report:sales")
    public R<List<StoreSalesCompareResp>> storeCompare(ReportTimeRangeReq req) {
        return R.ok(salesReportService.getStoreCompare(req));
    }

    /** 销售趋势:按日聚合销售金额与订单数 */
    @GetMapping("/sales/trend")
    @SaCheckPermission("business:report:sales")
    public R<List<SalesTrendResp>> salesTrend(ReportTimeRangeReq req) {
        return R.ok(salesReportService.getSalesTrend(req));
    }

    // ===================== 库存报表(4 个端点) =====================

    /** 库存周转率:出库成本 / 平均库存价值 */
    @GetMapping("/inventory/turnover")
    @SaCheckPermission("business:report:inventory")
    public R<List<InventoryTurnoverResp>> inventoryTurnover(ReportTimeRangeReq req) {
        return R.ok(inventoryReportService.getTurnover(req));
    }

    /** 滞销商品:指定时间范围内无出库动销的商品 */
    @GetMapping("/inventory/slow-moving")
    @SaCheckPermission("business:report:inventory")
    public R<List<SlowMovingResp>> slowMoving(ReportTimeRangeReq req) {
        return R.ok(inventoryReportService.getSlowMoving(req));
    }

    /** 缺货预警:可用库存低于安全库存阈值的商品 */
    @GetMapping("/inventory/alerts")
    @SaCheckPermission("business:report:inventory")
    public R<List<StockAlertResp>> stockAlerts(ReportTimeRangeReq req) {
        return R.ok(inventoryReportService.getStockAlerts(req));
    }

    /** 库存资金占用:按商品维度统计库存价值及占比 */
    @GetMapping("/inventory/fund")
    @SaCheckPermission("business:report:inventory")
    public R<List<StockFundResp>> stockFund(ReportTimeRangeReq req) {
        return R.ok(inventoryReportService.getStockFund(req));
    }

    // ===================== 订单报表(3 个端点) =====================

    /** 订单转化漏斗:按状态阶段统计订单数与转化率 */
    @GetMapping("/orders/funnel")
    @SaCheckPermission("business:report:order")
    public R<List<OrderFunnelResp>> orderFunnel(ReportTimeRangeReq req) {
        return R.ok(orderReportService.getOrderFunnel(req));
    }

    /** 退款分析:退款总金额 / 笔数 / 全额与部分退款占比 */
    @GetMapping("/orders/refund")
    @SaCheckPermission("business:report:order")
    public R<RefundAnalysisResp> refundAnalysis(ReportTimeRangeReq req) {
        return R.ok(orderReportService.getRefundAnalysis(req));
    }

    /** 客单价分析:GMV / 订单数 / 客单价 / 退款率 / 平均订单商品数 */
    @GetMapping("/orders/aov")
    @SaCheckPermission("business:report:order")
    public R<AovAnalysisResp> aovAnalysis(ReportTimeRangeReq req) {
        return R.ok(orderReportService.getAovAnalysis(req));
    }

    // ===================== 会员报表(3 个端点) =====================

    /** RFM 分群:基于 Recency/Frequency/Monetary 将会员分为 8 类 */
    @GetMapping("/members/rfm")
    @SaCheckPermission("business:report:member")
    public R<List<MemberRfmResp>> memberRfm(ReportTimeRangeReq req) {
        return R.ok(memberReportService.getRfm(req));
    }

    /** 会员等级分布:按 normal/silver/gold/diamond 统计人数与占比 */
    @GetMapping("/members/level-dist")
    @SaCheckPermission("business:report:member")
    public R<List<MemberLevelDistResp>> memberLevelDist(ReportTimeRangeReq req) {
        return R.ok(memberReportService.getLevelDist(req));
    }

    /** 会员增长趋势:按日统计新增会员数与活跃会员数 */
    @GetMapping("/members/growth")
    @SaCheckPermission("business:report:member")
    public R<List<MemberGrowthResp>> memberGrowth(ReportTimeRangeReq req) {
        return R.ok(memberReportService.getGrowth(req));
    }

    // ===================== 营销报表(2 个端点) =====================

    /** 优惠券核销率:按券模板统计发放数,已使用数及核销率 */
    @GetMapping("/coupons/redeem")
    @SaCheckPermission("business:report:coupon")
    public R<List<CouponRedeemResp>> couponRedeem(ReportTimeRangeReq req) {
        return R.ok(couponReportService.getRedeemRate(req));
    }

    /** 营销 ROI:按券模板统计折扣金额与带来销售额,计算投入产出比 */
    @GetMapping("/coupons/roi")
    @SaCheckPermission("business:report:coupon")
    public R<List<CouponRoiResp>> couponRoi(ReportTimeRangeReq req) {
        return R.ok(couponReportService.getRoi(req));
    }

    // ===================== 财务报表(2 个端点) =====================

    /** 财务汇总:总收入 / 退款金额 / 净收入 / 优惠金额 / 订单数 */
    @GetMapping("/finance/summary")
    @SaCheckPermission("business:report:finance")
    public R<FinanceSummaryResp> financeSummary(ReportTimeRangeReq req) {
        return R.ok(financeReportService.getFinanceSummary(req));
    }

    /** 支付方式分布:按微信/支付宝/余额/现金聚合金额与占比 */
    @GetMapping("/finance/pay-type")
    @SaCheckPermission("business:report:finance")
    public R<List<PayTypeDistResp>> payTypeDist(ReportTimeRangeReq req) {
        return R.ok(financeReportService.getPayTypeDist(req));
    }
}
