package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.CouponIssueReq;
import com.retail.business.dto.req.CouponTemplateCreateReq;
import com.retail.business.dto.req.CouponTemplateUpdateReq;
import com.retail.business.dto.resp.CouponIssueResp;
import com.retail.business.dto.resp.CouponTemplateListItemResp;
import com.retail.business.dto.resp.CouponTemplateResp;
import com.retail.business.service.CouponService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券模板管理接口.
 * <p>路由前缀 /api/v1/coupons.coupon_template 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:coupon:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>发放(issue)接口在同事务内创建 user_coupon 记录并原子累加模板 issued_count,
 * 避免内存值竞态与双写不一致.
 */
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * 创建优惠券模板(支持满减 / 折扣 / 代金券三种类型).
     * <p>状态由 Service 赋默认值 ACTIVE(铁律 6:CreateReq 禁 status 字段).
     */
    @PostMapping
    @SaCheckPermission("business:coupon:add")
    public R<CouponTemplateResp> create(@RequestBody CouponTemplateCreateReq req) {
        return R.ok(couponService.createTemplate(req));
    }

    /**
     * 分页查询优惠券模板(按状态 / 类型 / 关键词过滤).
     */
    @GetMapping
    @SaCheckPermission("business:coupon:query")
    public R<PageResp<CouponTemplateListItemResp>> list(@RequestParam(required = false) Integer status,
                                                        @RequestParam(required = false) Integer type,
                                                        @RequestParam(required = false) String keyword) {
        return R.ok(couponService.listTemplates(status, type, keyword));
    }

    /**
     * 查询优惠券模板详情(含类型 / 面额 / 使用门槛 / 有效期配置).
     */
    @GetMapping("/{couponId:\\d+}")
    @SaCheckPermission("business:coupon:query")
    public R<CouponTemplateResp> detail(@PathVariable Long couponId) {
        return R.ok(couponService.getTemplate(couponId));
    }

    /**
     * 修改优惠券模板(部分更新:名称 / 状态 / 发放总量 / 每人限领 / 有效期截止 / 使用门槛).
     */
    @PutMapping("/{couponId:\\d+}")
    @SaCheckPermission("business:coupon:edit")
    public R<CouponTemplateResp> update(@PathVariable Long couponId,
                                        @RequestBody CouponTemplateUpdateReq req) {
        return R.ok(couponService.updateTemplate(couponId, req));
    }

    /**
     * 删除优惠券模板(逻辑删除,由 BaseServiceImpl 填充 delete_at / delete_by 审计字段).
     */
    @DeleteMapping("/{couponId:\\d+}")
    @SaCheckPermission("business:coupon:remove")
    public R<Boolean> delete(@PathVariable Long couponId) {
        return R.ok(couponService.deleteTemplate(couponId));
    }

    /**
     * 批量发放优惠券给指定会员列表.
     * <p>逐条校验发放总量与每人限领,失败的会员单独记录不中断整体,
     * 成功条数 SQL 级原子累加模板 issued_count,避免并发竞态.
     */
    @PostMapping("/issue")
    @SaCheckPermission("business:coupon:issue")
    public R<CouponIssueResp> issue(@RequestBody CouponIssueReq req) {
        return R.ok(couponService.issue(req));
    }
}
