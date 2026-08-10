package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.convert.PointsConvert;
import com.retail.business.dto.req.PointsAdjustReq;
import com.retail.business.dto.resp.MemberPointsResp;
import com.retail.business.dto.resp.PointsLogResp;
import com.retail.business.entity.PointsLog;
import com.retail.business.service.PointsService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 会员积分管理接口.
 * <p>路由前缀 /api/v1/members/{memberId}/points.points_log 为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:points:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>积分业务变动(订单 earn / 退款 refund / 兑换 exchange)由订单 / 退款模块跨模块调用 PointsService,
 * 不经此 Controller;此 Controller 仅暴露流水查询,汇总查询,手动调整三个面向前端的接口.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId:\\d+}/points")
public class PointsController {

    private final PointsService pointsService;
    private final PointsConvert pointsConvert;

    /** 构造注入:单构造器由 Spring 自动注入,PointsConvert 用于 adjust 返回 PointsLog → PointsLogResp 转换. */
    public PointsController(PointsService pointsService, PointsConvert pointsConvert) {
        this.pointsService = pointsService;
        this.pointsConvert = pointsConvert;
    }

    /**
     * 分页查询会员积分流水(按变动类型 / 时间区间过滤).
     */
    @GetMapping("/logs")
    @SaCheckPermission("business:points:query")
    public R<PageResp<PointsLogResp>> logs(
            @PathVariable Long memberId,
            @RequestParam(value = "changeType", required = false) Integer changeType,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate) {
        return R.ok(pointsService.listLogs(memberId, changeType, startDate, endDate));
    }

    /**
     * 获取会员积分汇总(当前余额 + 累计获取 / 兑换 / 近 30 天获取 / 近 30 天消耗).
     */
    @GetMapping("/summary")
    @SaCheckPermission("business:points:query")
    public R<MemberPointsResp> summary(@PathVariable Long memberId) {
        return R.ok(pointsService.getPointsSummary(memberId));
    }

    /**
     * 手动调整积分(正数增加 / 负数扣减,需 adjust 权限).
     * <p>路径变量 memberId 覆盖请求体中的 memberId,保证以路径为准,避免调错会员.
     */
    @PostMapping("/adjust")
    @SaCheckPermission("business:points:adjust")
    public R<PointsLogResp> adjust(@PathVariable Long memberId, @RequestBody PointsAdjustReq req) {
        // 以路径变量 memberId 为准
        req.setMemberId(memberId);
        PointsLog log = pointsService.adjust(req);
        return R.ok(pointsConvert.toResp(log));
    }
}
