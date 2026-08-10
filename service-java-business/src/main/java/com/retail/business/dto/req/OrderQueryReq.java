package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单分页查询请求(运营后台订单管理列表页筛选).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 */
@Data
public class OrderQueryReq {

    private String orderNo;

    /** 目标会员 id, 对应 member.id. */
    private Long memberId;

    private String memberName;

    /** OrderStatus 枚举 code: 1=PENDING 待付款 2=PAID 已付款 3=SHIPPED 已发货 4=COMPLETED 已完成 5=CLOSED 已关闭 6=REFUNDING 退款中 7=REFUNDED 已退款. */
    private Integer status;

    /** OrderType 枚举 code: 1=NORMAL 正常 2=QUICK 闪购 3=FLASH_SALE 秒杀. */
    private Integer orderType;

    /** OrderChannel 枚举 code: 1=ONLINE 线上 2=AGENT Agent 3=MANUAL 手工. */
    private Integer channel;

    /** 下单起始日期(yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss, Asia/Shanghai); 可空. */
    private String startDate;

    /** 下单截止日期(yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss, Asia/Shanghai); 可空. */
    private String endDate;

    private String productName;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 实付金额下限(含), 与 maxAmount 组合区间查询. */
    private BigDecimal minAmount;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 实付金额上限(含), 与 minAmount 组合区间查询. */
    private BigDecimal maxAmount;

    /** PayType 枚举 code: 1=WECHAT 微信 2=ALIPAY 支付宝 3=BALANCE 余额 4=CASH 现金. */
    private Integer payType;

    /** 目标门店 id, 对应 sys_store.id; 可空, 门店拦截器自动按当前用户隔离, 传入仅用于平台管理员显式筛选. */
    private Long storeId;

    private String memberPhone;
}
