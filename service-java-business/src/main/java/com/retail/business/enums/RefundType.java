package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 退款类型枚举; refund_order.type 列.
 * <p>决定退款金额计算算法和库存回补范围(按订单项 OR 整单):
 * <ul>
 *   <li>FULL(1 全额退款): 整单退款; 全部 order_items 退款; refund_amount = 实付 pay_amount; 全部退回的商品回补 available.</li>
 *   <li>PARTIAL(2 部分退款): 指定 order_items 子集退款; refund_amount = sum(选中 item.pay_amount); 仅选中商品回补库存.</li>
 * </ul>
 */
public enum RefundType implements BaseEnum {

    /** 整单全额退款; 包含全部 order_items; refund_amount = 实付总金额 - 运费扣减; 全部商品回补库存; 若为整单全额退款且优惠券未使用则退回优惠券. */
    FULL(1, "全额退款"),
    /** 订单项部分退款; 选中的 order_items 子集; refund_amount = 被选 item pay_amount 之和; 库存按 item 逐项回补; 剩余 order_items 正常流转至 COMPLETED. */
    PARTIAL(2, "部分退款");

    @EnumValue
    private final Integer code;
    private final String desc;

    RefundType(Integer code, String desc) {
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
