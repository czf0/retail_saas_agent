package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.CouponRedeemResp;
import com.retail.business.dto.resp.report.CouponRoiResp;
import com.retail.business.mapper.UserCouponMapper;
import com.retail.business.service.CouponReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 优惠券报表 Service 实现.
 * <p>注入 UserCouponMapper,提供核销率与营销 ROI 分析.
 * 核销率 = 已使用数 / 发放数 × 100;ROI = 带来销售额 / 折扣金额.
 */
@Slf4j
@Service
public class CouponReportServiceImpl implements CouponReportService {

    private final UserCouponMapper userCouponMapper;

    /** 构造注入:单构造器由 Spring 自动注入 Mapper 依赖 */
    public CouponReportServiceImpl(UserCouponMapper userCouponMapper) {
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    public List<CouponRedeemResp> getRedeemRate(ReportTimeRangeReq req) {
        log.debug("查询优惠券核销率 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<CouponRedeemResp> list = userCouponMapper.selectRedeemRate(
                req.getStartDate(), req.getEndDate());
        // 计算核销率 = 已使用数 / 已发放数 × 100
        list.forEach(item -> {
            int issued = item.getIssuedCount() != null ? item.getIssuedCount() : 0;
            int used = item.getUsedCount() != null ? item.getUsedCount() : 0;
            item.setRedeemRate(calcPercentage(used, issued));
        });
        log.debug("查询优惠券核销率完成 优惠券数={}", list.size());
        return list;
    }

    @Override
    public List<CouponRoiResp> getRoi(ReportTimeRangeReq req) {
        log.debug("查询优惠券ROI startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<CouponRoiResp> list = userCouponMapper.selectCouponRoi(
                req.getStartDate(), req.getEndDate());
        // 计算 ROI = 带来销售额 / 折扣金额
        list.forEach(item -> {
            BigDecimal discount = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal sales = item.getBroughtSales() != null ? item.getBroughtSales() : BigDecimal.ZERO;
            if (discount.compareTo(BigDecimal.ZERO) != 0) {
                item.setRoi(sales.divide(discount, 2, RoundingMode.HALF_UP));
            } else {
                item.setRoi(BigDecimal.ZERO);
            }
        });
        log.debug("查询优惠券ROI完成 优惠券数={}", list.size());
        return list;
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
}
