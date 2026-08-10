package com.retail.business.dto.resp.report;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付方式分布报表行项(经营看板 → 支付占比饼图);按 pay_type 聚合订单实付金额与订单数.
 * <p>统计口径:
 * <ul>
 *   <li>payType 枚举映射: 1=WECHAT(微信支付), 2=ALIPAY(支付宝), 3=BALANCE(余额支付), 4=CASH(现金), 5=CARD(银行卡);具体见 {@link com.retail.business.enums.PayTypeEnum}.</li>
 *   <li>amount: SUM(order_info.pay_amount) WHERE pay_type = 枚举值;按 pay_time 分桶时间范围.</li>
 *   <li>orderCount: COUNT(DISTINCT order_info.id),同订单集.</li>
 *   <li>percentage: amount / SUM(所有 payType.amount) * 100;所有行求和 = 100%;混合支付(split_pay)按主 pay_type 归属.</li>
 * </ul>
 * <p>排除条件: 订单 status = CANCELED / PENDING 不计;pay_type = NULL 异常数据单独归为"其他"行(可配置是否展示).
 * <p>返回值: 每行 = 1 种支付方式;固定 5 行(按 payType 枚举顺序).
 */
@Data
public class PayTypeDistResp {

    /** 支付方式(wechat/alipay/balance/cash) */
    private Integer payType;

    /** 该支付方式金额合计 */
    private BigDecimal amount;

    /** 该支付方式订单数 */
    private Integer orderCount;

    /** 占总金额百分比(0-100) */
    private BigDecimal percentage;
}
