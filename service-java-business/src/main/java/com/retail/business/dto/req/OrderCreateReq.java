package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单创建请求(收银台/Agent 智能助手/运营后台手工下单).
 * <p>对应 Controller 路由: POST /api/v1/orders; status 字段 Service 层赋默认值(OrderStatus.PENDING=1, 铁律 6),
 * CreateReq 不承载 status.
 * <p>支持散客(memberId 为空)和会员订单; items 不能为空; 支持使用优惠券(userCouponId 可空).
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class OrderCreateReq {

    /** 目标会员 id, 对应 member.id; NULL=散客订单(非会员). */
    private Long memberId;

    /** OrderType 枚举 code: 1=NORMAL 正常 2=QUICK 闪购 3=FLASH_SALE 秒杀; 默认 1=NORMAL. */
    private Integer orderType;

    /** PayType 枚举 code: 1=WECHAT 微信 2=ALIPAY 支付宝 3=BALANCE 余额 4=CASH 现金; 可空, 为空时默认 4=CASH. */
    private Integer payType;

    /** OrderChannel 枚举 code: 1=ONLINE 线上 2=AGENT Agent 3=MANUAL 手工; 默认 1=ONLINE. */
    private Integer channel;

    /** 下单时间(Asia/Shanghai); 可空, 为空时由 Service 取当前系统时间. */
    private LocalDateTime orderTime;

    /** 用户优惠券 id, 对应 user_coupon.id; 核销用, 可空. */
    private Long userCouponId;

    private String remark;

    /** 订单明细列表; 必填, 至少 1 条, Service 层校验非空. */
    private List<OrderItemReq> items;

    /**
     * 租户 id, 对应 sys_tenant.id.
     * <p>租户用户由 TenantInterceptor 自动注入 tenant_id, 此字段忽略;
     * 平台管理员(tenantId=null)的 ignoreTable 跳过注入, 需前端传入顶栏已选租户 id, 后端手动设到实体.
     * 租户用户无需传此字段, 传了也会被忽略.
     */
    private Long tenantId;
}
