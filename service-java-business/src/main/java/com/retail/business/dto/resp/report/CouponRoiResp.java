package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券 ROI 报表行项(运营后台营销分析 → 优惠券投入产出比);衡量单张优惠券的营销效率 = 带来销售额 / 折扣投入.
 * <p>统计口径:
 * <ul>
 *   <li>discountAmount (投入): SUM(user_coupon.discount_value WHERE used);核销时实际抵扣的金额(已使用券的折扣值合计).</li>
 *   <li>broughtSales (带来销售额): SUM(order_info.pay_amount) WHERE order_info.coupon_id = 券.id 且 order_status != CANCELED;使用该券的订单实付金额(含未用券部分的商品销售额).</li>
 *   <li>roi (投入产出比): broughtSales / discountAmount;ROI = 5 表示每投入 1 元营销费用,带来 5 元销售额.discountAmount = 0 时 roi = NULL(无意义).</li>
 * </ul>
 * <p>排除条件: discountAmount = 0(如 0 元体验券)不计 ROI(避免无穷大);tenant_id 过滤.
 * <p>返回值: 每行 = 1 个优惠券模板;默认按 broughtSales DESC;可切换排序维度.
 */
@Data
public class CouponRoiResp {

    /** 优惠券模板ID */
    private Long couponId;

    /** 优惠券名称 */
    private String couponName;

    /** 折扣金额合计(该券核销时抵扣的金额) */
    private BigDecimal discountAmount;

    /** 带来销售额(使用该券的订单实付金额合计) */
    private BigDecimal broughtSales;

    /** ROI = 带来销售额 / 折扣金额 */
    private BigDecimal roi;
}
