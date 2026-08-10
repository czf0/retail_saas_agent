package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.FinanceSummaryResp;
import com.retail.business.dto.resp.report.PayTypeDistResp;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.service.FinanceReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 财务报表 Service 实现.
 * <p>注入 OrderInfoMapper,提供财务汇总与支付方式分布.
 * 财务汇总整合收入/退款/优惠数据;支付方式分布按金额占比聚合.
 */
@Slf4j
@Service
public class FinanceReportServiceImpl implements FinanceReportService {

    private final OrderInfoMapper orderInfoMapper;

    /** 构造注入:单构造器由 Spring 自动注入 Mapper 依赖 */
    public FinanceReportServiceImpl(OrderInfoMapper orderInfoMapper) {
        this.orderInfoMapper = orderInfoMapper;
    }

    @Override
    public FinanceSummaryResp getFinanceSummary(ReportTimeRangeReq req) {
        log.debug("查询财务汇总 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        FinanceSummaryResp resp = orderInfoMapper.selectFinanceSummary(
                req.getStartDate(), req.getEndDate());
        if (resp == null) {
            resp = new FinanceSummaryResp();
            resp.setTotalRevenue(BigDecimal.ZERO);
            resp.setRefundAmount(BigDecimal.ZERO);
            resp.setNetRevenue(BigDecimal.ZERO);
            resp.setDiscountAmount(BigDecimal.ZERO);
            resp.setOrderCount(0);
        }
        log.debug("查询财务汇总完成 totalRevenue={} netRevenue={} refundAmount={} orderCount={}",
                resp.getTotalRevenue(), resp.getNetRevenue(), resp.getRefundAmount(), resp.getOrderCount());
        return resp;
    }

    @Override
    public List<PayTypeDistResp> getPayTypeDist(ReportTimeRangeReq req) {
        log.debug("查询支付方式分布 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<PayTypeDistResp> list = orderInfoMapper.selectPayTypeDist(
                req.getStartDate(), req.getEndDate());
        // 计算各支付方式占总金额百分比
        BigDecimal totalAmount = list.stream()
                .map(PayTypeDistResp::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        list.forEach(item -> item.setPercentage(calcPercentage(item.getAmount(), totalAmount)));
        log.debug("查询支付方式分布完成 支付方式数={} totalAmount={}", list.size(), totalAmount);
        return list;
    }

    /**
     * 计算百分比 = part / total × 100,保留 2 位小数.
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
