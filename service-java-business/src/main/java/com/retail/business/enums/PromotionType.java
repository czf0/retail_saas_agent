package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 促销活动类型枚举; code 对齐 promotion_template.type 列(INT 1 ~ 3).
 * <p>决定折扣计算算法, 以及 promotion_template 中哪些字段具有语义含义:
 * <ul>
 *   <li>COUPON(1 优惠券类型): 传统领券模式; 用户需先领取 coupon_id; 由 CouponEngine 计算优惠; target_type 过滤适用商品.</li>
 *   <li>DISCOUNT(2 直减类型): 无需领券; 结算时若促销 ACTIVE + 商品在目标范围内则自动应用; subtotal 乘以 discountRate.</li>
 *   <li>FLASH_SALE(3 秒杀类型): 限时 + 限量; 固定 salePrice 覆盖原价; 独立 flash_sale_stock 池; 优惠券叠加禁用; 优先级最高.</li>
 * </ul>
 */
public enum PromotionType implements BaseEnum {

    /** 优惠券型促销; 用户先领券再结算使用; 关联 coupon_template; CouponEngine 按叠加规则计算优惠. */
    COUPON(1, "优惠券"),
    /** 直减型促销; 促销 ACTIVE 时在 [startTime,endTime] 窗口内对目标商品自动应用; discountRate * subtotal; 无需手动领券. */
    DISCOUNT(2, "折扣"),
    /** 限时秒杀型促销; 固定 salePrice 覆盖原价; 与主库存独立的 flash_sale_stock 池; 优先级覆盖 DISCOUNT/COUPON; 支付超时严格 5 分钟. */
    FLASH_SALE(3, "限时秒杀");

    @EnumValue
    private final Integer code;
    private final String desc;

    PromotionType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
