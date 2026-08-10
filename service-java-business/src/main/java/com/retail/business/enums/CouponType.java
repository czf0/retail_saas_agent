package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 优惠券模板类型枚举; code 对齐数据库 coupon_template.type 列(INT, 1 ~ 3).
 * <p>与 discountValue 字段语义耦合(discountValue 单位含义取决于类型):
 * <ul>
 *   <li>FULLCUT(1 满减券): discountValue 表示满减金额(单位: 元, 精度: 分); minAmount 字段控制达标门槛.</li>
 *   <li>DISCOUNT(2 折扣券): discountValue 表示折扣百分比(0.00 ~ 100.00, 如 80 = 8 折, 用户支付 80%).</li>
 *   <li>CASH(3 代金券): 无门槛, 直接抵扣 pay_amount 面值金额; minAmount 字段忽略, 典型用于补偿或新人福利.</li>
 * </ul>
 * <p>新增类型需同步修改铁律 4 对应的 BaseEnum, 并在 CouponServiceImpl.calcDiscount() 新增分支,
 * 否则会抛 ParamException"不支持的优惠券类型".
 */
public enum CouponType implements BaseEnum {

    /** 满减券; 订单小计 >= minAmount 门槛时, 从订单中减免 discountValue 元; 最终实付金额不可为负. */
    FULLCUT(1, "满减券"),
    /** 折扣券; 订单小计乘以 discountValue 折扣率(0.0-1.0, 如 0.8 = 实付 80%); 与其他优惠券是否可叠加取决于 coupon_template.exclude_group. */
    DISCOUNT(2, "折扣券"),
    /** 代金券 / 无门槛券; 不计订单金额, 直接从 pay_amount 抵扣面值金额; 典型用于补偿或新人福利发放. */
    CASH(3, "代金券");

    @EnumValue
    private final Integer code;
    private final String desc;

    CouponType(Integer code, String desc) {
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
