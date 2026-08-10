package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.CouponQueryReq;
import com.retail.business.dto.req.CouponReceiveReq;
import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.UserCouponResp;
import com.retail.business.entity.UserCoupon;
import com.retail.core.dto.PageResp;

import java.util.List;

/**
 * 用户优惠券服务.
 * <p>负责会员侧领券/核销/退券/查询.
 * <p>注意:use 与 refundByOrder 方法必须 public,供订单模块 OrderServiceImpl 跨模块调用.
 */
public interface UserCouponService extends IService<UserCoupon> {

    /**
     * 会员主动领取优惠券.
     * <p>校验:模板 active,未发放完(totalCount=0 不限),每人限领;
     * 计算 expire_time(relative=now+valid_days;fixed=valid_end);模板 issued_count++.
     */
    UserCouponResp receive(CouponReceiveReq req);

    /** 分页查询用户优惠券,支持 memberId/status/couponId/领取时间区间过滤. */
    PageResp<UserCouponListItemResp> listUserCoupons(CouponQueryReq req);

    /** 按会员 + 状态查询用户优惠券(不分页,用于会员侧列表). */
    List<UserCouponListItemResp> listByMember(Long memberId, Integer status);

    /**
     * 核销用户优惠券(订单支付时由 OrderServiceImpl 跨模块调用).
     * <p>校验:status=unused 且未过期;更新 status=used + orderId + orderNo + usedTime.
     */
    UserCouponResp use(Long userCouponId, Long orderId, String orderNo);

    /**
     * 按订单退券(订单退款时由 OrderServiceImpl 跨模块调用).
     * <p>将该订单关联的 used 状态 user_coupon 改为 refunded.
     *
     * @return 退券记录数
     */
    Integer refundByOrder(Long orderId);

    /**
     * 用户优惠券详情.
     * <p>注意:刻意不复用 {@code IService.getById}(其返回实体类型 UserCoupon),
     * 参照 {@code PromotionService.getPromotion} 用独立命名返回 Resp,避免与继承方法签名冲突.
     */
    UserCouponResp getUserCoupon(Long userCouponId);
}
