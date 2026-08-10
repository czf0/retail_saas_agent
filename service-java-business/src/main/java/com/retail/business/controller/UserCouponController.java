package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.CouponQueryReq;
import com.retail.business.dto.req.CouponReceiveReq;
import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.UserCouponResp;
import com.retail.business.service.UserCouponService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 用户优惠券管理接口.
 * <p>路由前缀 /api/v1/user-coupons.user_coupon 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission,列表 / 详情使用 business:usercoupon:query,
 * 领取 / 核销 / 退券统一复用 business:coupon:issue 权限(与发放同源,避免权限粒度过细).
 * <p>对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 */
@RestController
@RequestMapping("/api/v1/user-coupons")
public class UserCouponController {

    private final UserCouponService userCouponService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public UserCouponController(UserCouponService userCouponService) {
        this.userCouponService = userCouponService;
    }

    /**
     * 分页查询用户优惠券列表(按会员 / 状态 / 券模板过滤).
     */
    @GetMapping
    @SaCheckPermission("business:usercoupon:query")
    public R<PageResp<UserCouponListItemResp>> list(CouponQueryReq req) {
        return R.ok(userCouponService.listUserCoupons(req));
    }

    /**
     * 查询用户优惠券详情(含券名 / 面额 / 门槛 / 有效期 / 核销信息).
     */
    @GetMapping("/{userCouponId:\\d+}")
    @SaCheckPermission("business:usercoupon:query")
    public R<UserCouponResp> detail(@PathVariable Long userCouponId) {
        return R.ok(userCouponService.getUserCoupon(userCouponId));
    }

    /**
     * 会员主动领取优惠券(每人限领校验 + 发放总量校验,与批量发放共享权限).
     */
    @PostMapping("/receive")
    @SaCheckPermission("business:coupon:issue")
    public R<UserCouponResp> receive(@RequestBody CouponReceiveReq req) {
        return R.ok(userCouponService.receive(req));
    }

    /**
     * 核销用户优惠券(订单支付场景;订单支付时由订单模块直接调对应 Service.use,此接口供前端手动核销兜底).
     * <p>核销时将 orderId / orderNo 写入 user_coupon,退款时 refundByOrder 据此反查退券.
     */
    @PostMapping("/{userCouponId:\\d+}/use")
    @SaCheckPermission("business:coupon:issue")
    public R<UserCouponResp> use(@PathVariable Long userCouponId,
                                 @RequestParam Long orderId,
                                 @RequestParam String orderNo) {
        return R.ok(userCouponService.use(userCouponId, orderId, orderNo));
    }

    /**
     * 按订单退券(订单退款时由订单模块直接调对应 Service.refundByOrder,此接口供前端手动兜底).
     */
    @PostMapping("/refund-by-order/{orderId:\\d+}")
    @SaCheckPermission("business:coupon:issue")
    public R<Integer> refundByOrder(@PathVariable Long orderId) {
        return R.ok(userCouponService.refundByOrder(orderId));
    }
}
