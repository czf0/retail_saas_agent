package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.InventoryTurnoverResp;
import com.retail.business.dto.resp.report.SlowMovingResp;
import com.retail.business.dto.resp.report.StockAlertResp;
import com.retail.business.dto.resp.report.StockFundResp;
import com.retail.business.mapper.ProductStockMapper;
import com.retail.business.mapper.StockMovementMapper;
import com.retail.business.service.InventoryReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存报表 Service 实现.
 * <p>注入 ProductStockMapper / StockMovementMapper,通过 @Select 聚合查询 + Java 层合并计算.
 * 库存周转率需要合并出库成本(stock_movement)与库存价值(product_stock)两份数据.
 */
@Slf4j
@Service
public class InventoryReportServiceImpl implements InventoryReportService {

    private final ProductStockMapper productStockMapper;
    private final StockMovementMapper stockMovementMapper;

    /** 构造注入:单构造器由 Spring 自动注入全部 Mapper 依赖 */
    public InventoryReportServiceImpl(ProductStockMapper productStockMapper,
                                      StockMovementMapper stockMovementMapper) {
        this.productStockMapper = productStockMapper;
        this.stockMovementMapper = stockMovementMapper;
    }

    @Override
    public List<InventoryTurnoverResp> getTurnover(ReportTimeRangeReq req) {
        log.debug("查询库存周转率 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        // 1. 查询出库成本(按 product_id 聚合的 outbound 流水 × 成本价)
        List<InventoryTurnoverResp> outboundList = stockMovementMapper.selectOutboundCost(
                req.getStartDate(), req.getEndDate());
        // 2. 查询库存资金占用(含 stock_value = available_qty × cost)
        List<StockFundResp> stockFundList = productStockMapper.selectStockFund();
        // 3. 构建 productId → stockValue 映射,合并计算周转率
        Map<Long, BigDecimal> stockValueMap = new HashMap<>();
        for (StockFundResp sf : stockFundList) {
            stockValueMap.put(sf.getProductId(), sf.getStockValue());
        }
        for (InventoryTurnoverResp item : outboundList) {
            BigDecimal avgStockValue = stockValueMap.getOrDefault(item.getProductId(), BigDecimal.ZERO);
            item.setAvgStockValue(avgStockValue);
            // 周转率 = 出库成本 / 平均库存价值(库存为 0 时周转率设 0 避免除零)
            if (avgStockValue != null && avgStockValue.compareTo(BigDecimal.ZERO) != 0) {
                item.setTurnoverRate(item.getOutboundCost()
                        .divide(avgStockValue, 4, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP));
            } else {
                item.setTurnoverRate(BigDecimal.ZERO);
            }
        }
        log.debug("查询库存周转率完成 商品数={} 库存项数={}", outboundList.size(), stockFundList.size());
        return outboundList;
    }

    @Override
    public List<SlowMovingResp> getSlowMoving(ReportTimeRangeReq req) {
        log.debug("查询滞销商品 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        List<SlowMovingResp> list = stockMovementMapper.selectSlowMoving(
                req.getStartDate(), req.getEndDate());
        // 计算未销售天数:无出库记录时,从查询结束日往前推算(默认 30 天或时间范围跨度)
        LocalDate endDate = req.getEndDate() != null
                ? req.getEndDate().toLocalDate()
                : LocalDate.now();
        for (SlowMovingResp item : list) {
            if (item.getLastOutTime() != null) {
                long days = ChronoUnit.DAYS.between(item.getLastOutTime().toLocalDate(), endDate);
                item.setDaysNoSales((int) Math.max(days, 0));
            } else {
                // 从未出库,未销售天数设为时间范围跨度或 30 天默认值
                if (req.getStartDate() != null) {
                    long days = ChronoUnit.DAYS.between(req.getStartDate().toLocalDate(), endDate);
                    item.setDaysNoSales((int) Math.max(days, 30));
                } else {
                    item.setDaysNoSales(30);
                }
            }
        }
        log.debug("查询滞销商品完成 命中数={}", list.size());
        return list;
    }

    @Override
    public List<StockAlertResp> getStockAlerts(ReportTimeRangeReq req) {
        log.debug("查询缺货预警");
        List<StockAlertResp> list = productStockMapper.selectStockAlerts();
        // 缺货预警列表均为低于安全库存的商品,belowSafety 统一设为 true
        list.forEach(item -> item.setBelowSafety(true));
        log.debug("查询缺货预警完成 预警数={}", list.size());
        return list;
    }

    @Override
    public List<StockFundResp> getStockFund(ReportTimeRangeReq req) {
        log.debug("查询库存资金占用");
        List<StockFundResp> list = productStockMapper.selectStockFund();
        // 计算各商品占总库存资金百分比
        BigDecimal totalValue = list.stream()
                .map(StockFundResp::getStockValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        list.forEach(item -> item.setPercentage(calcPercentage(item.getStockValue(), totalValue)));
        log.debug("查询库存资金占用完成 商品数={} totalValue={}", list.size(), totalValue);
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
