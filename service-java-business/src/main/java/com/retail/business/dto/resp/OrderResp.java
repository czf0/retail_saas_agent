package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情页(ODP)展示响应;聚合订单主信息 + 订单项明细列表 + 实付/优惠/退款金额汇总 + 支付/完成时间节点.
 * <p>Controller: GET /api/v1/orders/{id:\\d+};{id} 正则守卫(铁律 26);仅订单所属会员或有订单查看权限的后台用户可访问.
 */
@Data
public class OrderResp {

    private Long id;

    /** 门店外键(sys_store.id);NULL = 租户级通用订单(无门店). */
    private Long storeId;

    /** 订单号(业务唯一键,YYYYMMDD + 8 位流水;分布式号段生成器保证不重复). */
    private String orderNo;

    /** 下单会员外键(member.id);NULL = 散客下单(未登录). */
    private Long memberId;

    /** 下单会员姓名/昵称快照(冗余存储下单当时 member.name,避免会员改名后历史订单对不上). */
    private String memberName;

    /** 订单类型:1=NORMAL(普通) 2=GIFT(赠品) 3=EXCHANGE(积分兑换) 4=PRESELL(预售);见 OrderTypeEnum. */
    private Integer orderType;

    /** 订单状态码:1=PENDING(待付) 2=PAID(已付) 3=SHIPPED(已发货) 4=COMPLETED(已完成) 5=CANCELED(已取消) 6=PARTIAL_REFUND(部分退款);见 OrderStatusEnum. */
    private Integer status;

    /** 订单状态中文描述(Service 层枚举映射,前端直接展示;不存 DB,每次动态计算). */
    private String statusDesc;

    /** 订单商品总金额(原价合计 = SUM(order_item.price * qty),未扣优惠;单位: 元,精度: 分). */
    private BigDecimal totalAmount;

    /** 优惠总金额 = 优惠券抵扣 + 促销满减 + 会员折扣 + 积分抵扣(合计;单位: 元). */
    private BigDecimal discountAmount;

    /** 实付金额 = totalAmount - discountAmount(三方支付成功回调后写入;单位: 元,精度: 分;财务对账用). */
    private BigDecimal payAmount;

    /** 已退款金额 = SUM(通过的退款单 refund_amount);若 = payAmount 表示全额退款完成. */
    private BigDecimal refundAmount;

    /** 支付方式:1=WECHAT 2=ALIPAY 3=BALANCE 4=CASH 5=CARD;见 PayTypeEnum;混合支付取主支付方式. */
    private Integer payType;

    /** 支付成功时间(三方支付回调写入,非用户点击支付瞬间;财务对账时间基准;时区 Asia/Shanghai). */
    private LocalDateTime payTime;

    /** 下单渠道:1=H5 2=小程序 3=APP 4=PC后台 5=POS;见 OrderChannelEnum. */
    private Integer channel;

    /** 下单时间(用户提交订单瞬间,前端传入后端校验落库;非 payTime). */
    private LocalDateTime orderTime;

    /** 订单完成时间(用户确认收货 or 自动售后期满 N 天;COMPLETED 状态流转时间). */
    private LocalDateTime finishTime;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 订单项明细(1:N,order_item 按 order_id 查询聚合);详情返回时包含 item 级 refundable 子状态. */
    private List<OrderItemResp> items;
}
