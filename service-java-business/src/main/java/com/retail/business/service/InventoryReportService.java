package com.retail.business.service;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.InventoryTurnoverResp;
import com.retail.business.dto.resp.report.SlowMovingResp;
import com.retail.business.dto.resp.report.StockAlertResp;
import com.retail.business.dto.resp.report.StockFundResp;

import java.util.List;

/**
 * 库存报表 Service.
 * <p>整合 product_stock / stock_movement / product_info 数据,提供 4 类库存分析报表:
 * 库存周转率,滞销商品,缺货预警,库存资金占用.
 */
public interface InventoryReportService {

    /**
     * 库存周转率:出库成本 / 平均库存价值.
     *
     * @param req 时间范围 + 过滤参数
     * @return 各商品库存周转率列表
     */
    List<InventoryTurnoverResp> getTurnover(ReportTimeRangeReq req);

    /**
     * 滞销商品:指定时间范围内无出库动销记录的商品.
     *
     * @param req 时间范围 + 过滤参数
     * @return 滞销商品列表
     */
    List<SlowMovingResp> getSlowMoving(ReportTimeRangeReq req);

    /**
     * 缺货预警:可用库存低于安全库存阈值的商品.
     *
     * @param req 时间范围 + 过滤参数(库存报表通常不按时间过滤,参数保留扩展)
     * @return 缺货预警列表
     */
    List<StockAlertResp> getStockAlerts(ReportTimeRangeReq req);

    /**
     * 库存资金占用:按商品维度统计库存价值及占比.
     *
     * @param req 时间范围 + 过滤参数
     * @return 库存资金占用列表
     */
    List<StockFundResp> getStockFund(ReportTimeRangeReq req);
}
