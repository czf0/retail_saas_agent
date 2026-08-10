package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.CategorySalesResp;
import com.retail.business.dto.resp.report.ProductSalesRankResp;
import com.retail.business.dto.resp.report.SalesSummaryResp;
import com.retail.business.dto.resp.report.SalesTrendResp;
import com.retail.business.dto.resp.report.StoreSalesCompareResp;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.mapper.OrderItemMapper;
import com.retail.business.service.SalesReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 销售报表 Service 实现.
 * <p>注入 OrderInfoMapper / OrderItemMapper,通过 @Select 聚合查询直接获取报表数据.
 * tenant_id / store_id 由拦截器自动注入,SQL 无需显式声明过滤条件.
 * <p>百分比计算在 Java 层完成,避免 SQL 嵌套过深.
 */
@Slf4j
@Service
public class SalesReportServiceImpl implements SalesReportService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;

    /** 构造注入:单构造器由 Spring 自动注入全部 Mapper 依赖,字段不可变,便于单元测试 */
    public SalesReportServiceImpl(OrderInfoMapper orderInfoMapper,
                                  OrderItemMapper orderItemMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public SalesSummaryResp getSummary(ReportTimeRangeReq req) {
        log.debug("查询销售汇总 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        // 查询聚合数据(SQL 已用 COALESCE 保证非 null)
        SalesSummaryResp resp = orderInfoMapper.selectSalesSummary(req.getStartDate(), req.getEndDate());
        if (resp == null) {
            resp = new SalesSummaryResp();
            resp.setTotalGmv(BigDecimal.ZERO);
            resp.setOrderCount(0);
            resp.setAvgOrderValue(BigDecimal.ZERO);
            resp.setRefundAmount(BigDecimal.ZERO);
            resp.setTotalDiscount(BigDecimal.ZERO);
        }
        // 计算退款率 = 退款金额 / 总GMV × 100
        resp.setRefundRate(calcPercentage(resp.getRefundAmount(), resp.getTotalGmv()));
        log.debug("查询销售汇总完成 totalGmv={} orderCount={} refundRate={}",
                resp.getTotalGmv(), resp.getOrderCount(), resp.getRefundRate());
        return resp;
    }

    @Override
    public List<ProductSalesRankResp> getProductRank(ReportTimeRangeReq req) {
        log.debug("查询商品销售排名 startDate={} endDate={} productId={}",
                req.getStartDate(), req.getEndDate(), req.getProductId());
        List<ProductSalesRankResp> list = orderItemMapper.selectProductSalesRank(
                req.getStartDate(), req.getEndDate(), req.getProductId());
        // 填充排名字段(SQL 已按 sales_amount DESC 排序)
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        log.debug("查询商品销售排名完成 命中数={}", list.size());
        return list;
    }

    @Override
    public List<CategorySalesResp> getCategorySales(ReportTimeRangeReq req) {
        log.debug("查询分类销售 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<CategorySalesResp> list = orderItemMapper.selectCategorySales(
                req.getStartDate(), req.getEndDate());
        // 计算各分类占总销售额百分比
        BigDecimal totalSales = list.stream()
                .map(CategorySalesResp::getSalesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        list.forEach(item -> item.setPercentage(calcPercentage(item.getSalesAmount(), totalSales)));
        log.debug("查询分类销售完成 类目数={} totalSales={}", list.size(), totalSales);
        return list;
    }

    @Override
    public List<StoreSalesCompareResp> getStoreCompare(ReportTimeRangeReq req) {
        log.debug("查询门店销售对比 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<StoreSalesCompareResp> list = orderInfoMapper.selectStoreCompare(
                req.getStartDate(), req.getEndDate());
        log.debug("查询门店销售对比完成 门店数={}", list.size());
        return list;
    }

    @Override
    public List<SalesTrendResp> getSalesTrend(ReportTimeRangeReq req) {
        log.debug("查询销售趋势 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<SalesTrendResp> list = orderInfoMapper.selectSalesTrend(
                req.getStartDate(), req.getEndDate());
        log.debug("查询销售趋势完成 数据点={}", list.size());
        return list;
    }

    /**
     * 计算百分比 = part / total × 100,保留 2 位小数.
     * total 为 0 时返回 0,避免除零异常.
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
