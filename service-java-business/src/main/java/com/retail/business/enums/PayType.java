package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 支付方式枚举; code = 1 微信支付, 2 支付宝, 3 余额支付, 4 现金.
 * <p>映射至 order_info.pay_type + pay_flow.pay_channel; 决定退款回调调用哪个三方支付网关 SDK:
 * <ul>
 *   <li>WECHAT(1 微信支付): 调用微信支付 JSAPI/Native SDK; pay_flow 记录微信回调返回的 trade_no.</li>
 *   <li>ALIPAY(2 支付宝): 调用支付宝 SDK; pay_flow 记录支付宝回调返回的 trade_no.</li>
 *   <li>BALANCE(3 余额支付): 从用户 member.balance 字段扣减; 原子 CAS 更新防负数; 无三方回调.</li>
 *   <li>CASH(4 现金): MANUAL 渠道订单的线下现金支付; pay_flow 有记录但无回调; 运营手动录入实收现金.</li>
 * </ul>
 */
public enum PayType implements BaseEnum {

    /** 微信在线支付; H5/小程序用 JSAPI, 扫码用 Native; 异步回调更新 OrderStatus.PAID; 退款调用微信退款 API 并传原 trade_no. */
    WECHAT(1, "微信支付"),
    /** 支付宝在线支付; 移动端/PC 网页支付宝 SDK; 异步回调更新 OrderStatus.PAID; 退款调用支付宝退款 API 并传原 trade_no. */
    ALIPAY(2, "支付宝"),
    /** 会员钱包余额支付; 原子 CAS 校验扣减 member.balance; 无三方回调; 立即标记 PAID; 退款直接退回至余额. */
    BALANCE(3, "余额支付"),
    /** MANUAL 后台渠道的线下现金支付; 无三方网关; 运营登记实收现金; 后台服务同步创建 pay_flow 现金流水. */
    CASH(4, "现金");

    @EnumValue
    private final Integer code;
    private final String desc;

    PayType(Integer code, String desc) {
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
