package com.retail.business.service;

import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.StatsOverviewReq;
import com.retail.business.dto.resp.InventoryRecordResp;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.OrderTrendResp;
import com.retail.business.dto.resp.SalesRecordResp;
import com.retail.business.dto.resp.StatsOverviewResp;

import java.util.List;

/**
 * 统计概览服务.
 * <p>
 * sales_record / inventory_record / order_trend / member 表均为多租户表,
 * tenant_id 由拦截器自动注入过滤,无需手动处理.
 */
public interface StatsService {

    List<SalesRecordResp> querySales(String startDate, String endDate);

    List<InventoryRecordResp> queryInventory(Boolean lowStockOnly);

    List<OrderTrendResp> queryOrderTrend(String startDate, String endDate);

    PageResp<MemberResp> queryMembers(String level);

    StatsOverviewResp overview();

    /**
     * 查询经营概览(支持按创建时间范围与门店过滤).
     * <p>
     * 各指标计数按创建时间范围过滤;storeId 对商品/促销/评价等门店维度指标过滤.
     */
    StatsOverviewResp overview(StatsOverviewReq req);
}
