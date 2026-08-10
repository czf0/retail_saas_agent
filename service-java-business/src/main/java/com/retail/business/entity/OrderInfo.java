package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.OrderChannel;
import com.retail.business.enums.OrderStatus;
import com.retail.business.enums.OrderType;
import com.retail.business.enums.PayType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体, 对应数据库 order_info 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); store_id 为空表示租户级汇总, 批量任务无上下文时跳过注入.
 * <p>业务约束: 零售订单核心实体, 承载订单状态机与支付/退款金额核算; 状态流转 PENDING_PAY -> PAID -> SHIPPED -> COMPLETED / CLOSED / REFUNDING -> REFUNDED(由 OrderServiceImpl 状态机校验, 非法跳转抛 BizException).
 * <p>唯一约束: UNIQUE(tenant_id, order_no), 同一租户下业务订单号不可重复; pay_no(第三方交易流水号, 财务对账用, 预留字段)UNIQUE 全局唯一.
 * <p>金额核算公式: pay_amount = total_amount - discount_amount(实付金额); refund_amount <= pay_amount(退款不能超过实付).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("order_info")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 id(TenantInterceptor 自动注入 WHERE 条件, 无 @TableField(fill), 纯靠 SQL 层注入). */
    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入, NULL=租户级汇总; 批量任务无 storeId 上下文时跳过注入; 店长登录后自动限定到此值). */
    private Long storeId;

    /** 业务订单号(UNIQUE(tenant_id, order_no), 租户内唯一); 生成规则: yyyyMMddHHmmss + 4 位随机数, 共 18 位; POS 小票打印 / 退款单 / 对账主键使用此值. */
    private String orderNo;

    /** 会员 id, 指向 member.id; NULL=散客(不关联会员, 不累计积分不累计消费). */
    private Long memberId;

    /** 会员姓名冗余快照(下单时从会员读取写入), 避免订单列表查询 JOIN member; 会员改名不影响历史订单. */
    private String memberName;

    /** 订单类型(OrderType 枚举本体: 1=NORMAL 正常, 2=FLASH_SALE 闪购, 3=SECKILL 秒杀); 秒杀/闪购由促销引擎独立计算折扣(优先于普通优惠券叠加). */
    private OrderType orderType;

    /** 订单状态(OrderStatus 枚举本体: 1=PENDING_PAY 待付, 2=PAID 已付, 3=SHIPPED 已发, 4=COMPLETED 完成, 5=CLOSED 关闭, 6=REFUNDING 退款中, 7=REFUNDED 已退款); 非法状态跳转 Service 层抛 BizException. */
    private OrderStatus status;

    /** 商品总金额(所有 order_item.subtotal 之和, 未折扣前原价总额); 单位: 元, 精度: 分, DECIMAL(12,2). */
    private BigDecimal totalAmount;

    /** 优惠金额(优惠券抵扣 + 促销折扣 + 会员折扣 合计); 明细分摊: 按 order_item.subtotal 占比分配, 保留两位小数, 差额尾差由最后一条明细承担. */
    private BigDecimal discountAmount;

    /** 实付金额 = total_amount - discount_amount; 单位: 元, 精度: 分, DECIMAL(12,2); 三方支付回调以此金额对账(pay_amount 与支付回调 amount 必须分毫不差). */
    private BigDecimal payAmount;

    /** 已退款金额(累加值, 所有关联 order_refund.refund_amount 之和); 上限 = pay_amount, 达到后订单状态自动改为 REFUNDED. */
    private BigDecimal refundAmount;

    /** 支付方式(PayType 枚举本体: 1=WECHAT 微信, 2=ALIPAY 支付宝, 3=BALANCE 余额, 4=CASH 现金); 组合支付时记录主支付方式, 明细记录 pending. */
    private PayType payType;

    /** 支付完成时间(Asia/Shanghai 时区, 三方支付回调成功时写入); 超时未支付(默认 30 分钟)自动关单, status 改为 CLOSED. */
    private LocalDateTime payTime;

    /** 订单渠道(OrderChannel 枚举本体: 1=ONLINE 线上小程序, 2=AGENT Agent 智能下单, 3=MANUAL 手工录单); 报表按渠道分组分析 GMV 构成. */
    private OrderChannel channel;

    /** 下单时间(Asia/Shanghai 时区, 订单创建时写入, 超时关单计时器起点). */
    private LocalDateTime orderTime;

    /** 完成时间(Asia/Shanghai 时区, status 变为 COMPLETED 时填充); 会员积分/消费累计/销售报表确认以此时间为准. */
    private LocalDateTime finishTime;

    private String remark;

    /** 收货人姓名(Agent 工具 order:update 改收货用; 散客必填, 会员默认取会员档案 name, 可改). */
    private String receiverName;

    /** 收货人手机号(Agent 工具 order:update 改收货用; 散客必填, 会员默认取 member.phone, 可改). */
    private String receiverPhone;

    /** 完整收货地址(省市区 + 详细地址拼接字符串; Agent 工具 order:update 改收货用; 门店自提时填 "门店自提"). */
    private String receiverAddress;

    /** 第三方支付平台交易流水号(UNIQUE 全局唯一, 财务对账用); 微信 payment.transaction_id / 支付宝 trade_no; 支付回调成功后写入, NULL=现金/余额支付无三方流水. */
    // private String payNo;

    /** 商户内部交易号(传给三方支付的 out_trade_no, 全局唯一); 用于对账时与 payNo 双向关联; 生成规则: 前缀 TP + yyyyMMddHHmmss + 6 位随机, 共 24 位. */
    // private String tradeNo;

    private Integer deleted = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    private LocalDateTime deleteAt;

    private String deleteBy;
}
