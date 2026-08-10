package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.CouponRedeemResp;
import com.retail.business.dto.resp.report.CouponRoiResp;

import java.util.List;

/**
 * 优惠券报表 Service.
 * <p>整合 user_coupon / coupon_template / order_info 数据,提供 2 类营销分析报表:
 * 优惠券核销率,营销 ROI.
 */
public interface CouponReportService {

    /**
     * 优惠券核销率:按券模板统计发放数,已使用数及核销率.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各优惠券核销率列表
     */
    List<CouponRedeemResp> getRedeemRate(ReportTimeRangeReq req);

    /**
     * 营销 ROI:按券模板统计折扣金额与带来销售额,计算投入产出比.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各优惠券 ROI 列表
     */
    List<CouponRoiResp> getRoi(ReportTimeRangeReq req);
}
