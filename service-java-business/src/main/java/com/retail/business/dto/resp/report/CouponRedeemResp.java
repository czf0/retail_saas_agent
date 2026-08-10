package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券核销率报表行项(运营后台营销分析 → 优惠券效果列表);按券模板维度统计发放与核销指标.
 * <p>统计口径:
 * <ul>
 *   <li>issuedCount (已发放数): COUNT(user_coupon.id) WHERE user_coupon.coupon_id = 模板.id;含已使用/未使用/已过期.</li>
 *   <li>usedCount (已核销数): COUNT(user_coupon.id) WHERE user_coupon.status = 'USED' 且 used_time 在查询范围;或 user_coupon.order_id 非空且对应订单已支付.</li>
 *   <li>redeemRate (核销率): usedCount / issuedCount * 100;issuedCount = 0 时 redeemRate = 0(避免除零).</li>
 * </ul>
 * <p>排除条件: 已删除模板(coupon_template.deleted = 1)仍展示历史数据;测试租户不计入正式数据.
 * <p>返回值: 每行 = 1 个优惠券模板;默认按 redeemRate DESC 或 issuedCount DESC(可配置排序字段).
 */
@Data
public class CouponRedeemResp {

    /** 优惠券模板ID */
    private Long couponId;

    /** 优惠券名称 */
    private String couponName;

    /** 已发放数量 */
    private Integer issuedCount;

    /** 已使用(核销)数量 */
    private Integer usedCount;

    /** 核销率 = 已使用数 / 已发放数 × 100(百分比) */
    private BigDecimal redeemRate;
}
