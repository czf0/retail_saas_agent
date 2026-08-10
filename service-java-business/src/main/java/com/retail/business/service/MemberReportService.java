package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.dto.resp.report.MemberLevelDistResp;
import com.retail.business.dto.resp.report.MemberRfmResp;

import java.util.List;

/**
 * 会员报表 Service.
 * <p>整合 member / order_info 数据,提供 3 类会员分析报表:
 * RFM 分群,等级分布,增长趋势.
 */
public interface MemberReportService {

    /**
     * RFM 分群:基于 Recency / Frequency / Monetary 三维度,以中位数为阈值将会员分为 8 类.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各客群 RFM 分群数据列表
     */
    List<MemberRfmResp> getRfm(ReportTimeRangeReq req);

    /**
     * 会员等级分布:按 normal/silver/gold/diamond 统计人数与占比.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各等级分布数据列表
     */
    List<MemberLevelDistResp> getLevelDist(ReportTimeRangeReq req);

    /**
     * 会员增长趋势:按日统计新增会员数与活跃会员数.
     *
     * @param req 时间范围 + 过滤参数
     * @return 每日会员增长数据列表
     */
    List<MemberGrowthResp> getGrowth(ReportTimeRangeReq req);
}
