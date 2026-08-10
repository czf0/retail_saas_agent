package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.resp.InventoryRecordResp;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.OrderTrendResp;
import com.retail.business.dto.resp.SalesRecordResp;
import com.retail.business.dto.resp.StatsOverviewResp;
import com.retail.business.service.StatsService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计概览接口.
 * <p>路由前缀 /api/v1/stats.统计表均为多租户表,tenant_id 由拦截器自动过滤,
 * 未登录(无租户上下文)访问时由多租户拦截器抛出 TenantException.
 * <p>权限校验基于 @SaCheckPermission("business:stats:query") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>所有统计接口为敏感财务 / 库存 / 会员聚合查询,统一需 business:stats:query 权限.
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 销售统计(按日聚合销售额,支持时间区间过滤,敏感财务查询需权限).
     */
    @GetMapping("/sales")
    @SaCheckPermission("business:stats:query")
    public R<List<SalesRecordResp>> sales(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return R.ok(statsService.querySales(startDate, endDate));
    }

    /**
     * 库存统计(库存总值 / 低库存数量,敏感库存查询需权限).
     */
    @GetMapping("/inventory")
    @SaCheckPermission("business:stats:query")
    public R<List<InventoryRecordResp>> inventory(
            @RequestParam(value = "lowStockOnly", required = false) Boolean lowStockOnly) {
        return R.ok(statsService.queryInventory(lowStockOnly));
    }

    /**
     * 订单趋势(订单数 / 销售额趋势,按日聚合,敏感财务查询需权限).
     */
    @GetMapping("/order-trend")
    @SaCheckPermission("business:stats:query")
    public R<List<OrderTrendResp>> orderTrend(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return R.ok(statsService.queryOrderTrend(startDate, endDate));
    }

    /**
     * 会员统计(按等级筛选会员列表,敏感会员查询需权限).
     */
    @GetMapping("/members")
    @SaCheckPermission("business:stats:query")
    public R<PageResp<MemberResp>> members(
            @RequestParam(value = "level", required = false) String level) {
        return R.ok(statsService.queryMembers(level));
    }

    /**
     * 概览统计(GMV / 订单数 / 会员数 / 库存总值等核心指标总览,敏感聚合查询需权限).
     */
    @GetMapping("/overview")
    @SaCheckPermission("business:stats:query")
    public R<StatsOverviewResp> overview() {
        return R.ok(statsService.overview());
    }
}
