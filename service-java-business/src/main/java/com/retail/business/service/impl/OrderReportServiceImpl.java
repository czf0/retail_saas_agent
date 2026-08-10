package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.AovAnalysisResp;
import com.retail.business.dto.resp.report.OrderFunnelResp;
import com.retail.business.dto.resp.report.RefundAnalysisResp;
import com.retail.business.dto.resp.report.SalesSummaryResp;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.mapper.OrderRefundMapper;
import com.retail.business.service.OrderReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 订单报表 Service 实现.
 * <p>注入 OrderInfoMapper / OrderRefundMapper,提供订单漏斗 / 退款分析 / 客单价分析.
 */
@Slf4j
@Service
public class OrderReportServiceImpl implements OrderReportService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderRefundMapper orderRefundMapper;

    /** 构造注入:单构造器由 Spring 自动注入全部 Mapper 依赖 */
    public OrderReportServiceImpl(OrderInfoMapper orderInfoMapper,
                                  OrderRefundMapper orderRefundMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderRefundMapper = orderRefundMapper;
    }

    @Override
    public List<OrderFunnelResp> getOrderFunnel(ReportTimeRangeReq req) {
        log.debug("查询订单漏斗");
        List<OrderFunnelResp> list = orderInfoMapper.selectOrderFunnel();
        // 计算各阶段占总订单数百分比
        int total = list.stream().mapToInt(OrderFunnelResp::getCount).sum();
        list.forEach(item ->
                item.setPercentage(calcPercentage(item.getCount(), total)));
        log.debug("查询订单漏斗完成 阶段数={} totalOrders={}", list.size(), total);
        return list;
    }

    @Override
    public RefundAnalysisResp getRefundAnalysis(ReportTimeRangeReq req) {
        log.debug("查询退款分析 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        RefundAnalysisResp resp = orderRefundMapper.selectRefundAnalysis(
                req.getStartDate(), req.getEndDate());
        if (resp == null) {
            resp = new RefundAnalysisResp();
            resp.setTotalRefundAmount(BigDecimal.ZERO);
            resp.setRefundOrderCount(0);
            resp.setFullRefundCount(0);
            resp.setPartialRefundCount(0);
            resp.setAvgRefundAmount(BigDecimal.ZERO);
        }
        log.debug("查询退款分析完成 totalRefundAmount={} refundOrderCount={} full={} partial={}",
                resp.getTotalRefundAmount(), resp.getRefundOrderCount(),
                resp.getFullRefundCount(), resp.getPartialRefundCount());
        return resp;
    }

    @Override
    public AovAnalysisResp getAovAnalysis(ReportTimeRangeReq req) {
        log.debug("查询客单价分析 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        // 复用销售汇总数据 + 追加平均订单商品数
        SalesSummaryResp summary = orderInfoMapper.selectSalesSummary(
                req.getStartDate(), req.getEndDate());
        Integer totalItems = orderInfoMapper.selectTotalItemsCount(
                req.getStartDate(), req.getEndDate());

        AovAnalysisResp resp = new AovAnalysisResp();
        if (summary != null) {
            resp.setTotalGmv(summary.getTotalGmv() != null ? summary.getTotalGmv() : BigDecimal.ZERO);
            resp.setOrderCount(summary.getOrderCount() != null ? summary.getOrderCount() : 0);
            resp.setAvgOrderValue(summary.getAvgOrderValue() != null ? summary.getAvgOrderValue() : BigDecimal.ZERO);
            // 退款率 = 退款金额 / 总GMV × 100
            BigDecimal refundAmount = summary.getRefundAmount() != null ? summary.getRefundAmount() : BigDecimal.ZERO;
            resp.setRefundRate(calcPercentage(refundAmount, resp.getTotalGmv()));
        } else {
            resp.setTotalGmv(BigDecimal.ZERO);
            resp.setOrderCount(0);
            resp.setAvgOrderValue(BigDecimal.ZERO);
            resp.setRefundRate(BigDecimal.ZERO);
        }
        // 平均订单商品数 = 总商品件数 / 订单数
        int itemCount = totalItems != null ? totalItems : 0;
        if (resp.getOrderCount() > 0) {
            resp.setAvgItemsPerOrder(BigDecimal.valueOf(itemCount)
                    .divide(BigDecimal.valueOf(resp.getOrderCount()), 2, RoundingMode.HALF_UP));
        } else {
            resp.setAvgItemsPerOrder(BigDecimal.ZERO);
        }
        log.debug("查询客单价分析完成 totalGmv={} orderCount={} avgItemsPerOrder={}",
                resp.getTotalGmv(), resp.getOrderCount(), resp.getAvgItemsPerOrder());
        return resp;
    }

    /**
     * 计算百分比 = count / total × 100,保留 2 位小数.
     */
    private BigDecimal calcPercentage(int count, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算百分比 = part / total × 100,保留 2 位小数(BigDecimal 版本).
     */
    private BigDecimal calcPercentage(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
