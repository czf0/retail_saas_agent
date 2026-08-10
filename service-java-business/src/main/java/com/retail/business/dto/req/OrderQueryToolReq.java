package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 工具专用入参: 订单查询工具(order:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>订单定位: 支持 orderNo/memberName/productName 等多维自然语言解析, 不只依赖 orderId(铁律 20).
 */
@Data
public class OrderQueryToolReq {

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

    /** 下单起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 下单截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    private String productName;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 实付金额下限(含), 与 maxAmount 组合区间查询. */
    private BigDecimal minAmount;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 实付金额上限(含), 与 minAmount 组合区间查询. */
    private BigDecimal maxAmount;

    /** PayType 枚举 code: 1=WECHAT 微信 2=ALIPAY 支付宝 3=BALANCE 余额 4=CASH 现金. */
    private Integer payType;

    /** 目标门店 id, 对应 sys_store.id; 可空, 平台管理员显式筛选用. */
    private Long storeId;

    private String memberPhone;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
