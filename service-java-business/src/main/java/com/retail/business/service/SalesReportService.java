package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.CategorySalesResp;
import com.retail.business.dto.resp.report.ProductSalesRankResp;
import com.retail.business.dto.resp.report.SalesSummaryResp;
import com.retail.business.dto.resp.report.SalesTrendResp;
import com.retail.business.dto.resp.report.StoreSalesCompareResp;

import java.util.List;

/**
 * 销售报表 Service.
 * <p>整合 order_info / order_item 数据,提供 5 类销售分析报表:
 * 销售汇总,商品销售排行,分类销售占比,门店销售对比,销售趋势.
 */
public interface SalesReportService {

    /**
     * 销售汇总:总GMV / 订单数 / 客单价 / 退款率 / 优惠金额.
     *
     * @param req 时间范围 + 过滤参数
     * @return 销售汇总数据
     */
    SalesSummaryResp getSummary(ReportTimeRangeReq req);

    /**
     * 商品销售排行:按商品维度聚合销量与金额,降序排列.
     *
     * @param req 时间范围 + 过滤参数(productId 可指定单商品)
     * @return 商品销售排行列表
     */
    List<ProductSalesRankResp> getProductRank(ReportTimeRangeReq req);

    /**
     * 分类销售占比:按分类维度聚合销售金额,计算占比.
     *
     * @param req 时间范围 + 过滤参数
     * @return 分类销售列表
     */
    List<CategorySalesResp> getCategorySales(ReportTimeRangeReq req);

    /**
     * 门店销售对比:按门店维度聚合销售金额 / 订单数 / 客单价.
     *
     * @param req 时间范围 + 过滤参数
     * @return 门店销售对比列表
     */
    List<StoreSalesCompareResp> getStoreCompare(ReportTimeRangeReq req);

    /**
     * 销售趋势:按日聚合销售金额与订单数.
     *
     * @param req 时间范围 + 过滤参数
     * @return 每日销售趋势列表
     */
    List<SalesTrendResp> getSalesTrend(ReportTimeRangeReq req);
}
