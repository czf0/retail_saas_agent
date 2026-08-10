package com.retail.business.service;

import com.retail.business.dto.req.OrderCreateReq;
import com.retail.business.dto.req.OrderPayReq;
import com.retail.business.dto.req.OrderQueryReq;
import com.retail.business.dto.req.OrderUpdateReq;
import com.retail.business.dto.resp.OrderCreateResp;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.dto.resp.OrderPayResp;
import com.retail.business.dto.resp.OrderResp;
import com.retail.business.dto.resp.OrderUpdateResp;
import com.retail.core.dto.PageResp;

/**
 * 订单服务接口.
 * <p>封装订单生命周期管理:创建 → 支付 → 发货 → 完成 / 取消.
 * <p>跨模块联动:
 * <ul>
 *   <li>支付成功 → 库存出库({@code StockService.outbound})</li>
 *   <li>订单完成 → 会员积分获取({@code PointsService.earn})+ 会员汇总更新({@code MemberMapper.incTotalOrders})</li>
 *   <li>退款审核通过 → 退券 + 退积分 + 库存回滚(由 {@code RefundService.auditRefund} 编排)</li>
 * </ul>
 */
public interface OrderService {

    /**
     * 创建订单(含明细列表).状态初始化为 PENDING.
     * <p>会校验:items 非空,商品存在,库存充足(仅校验不扣减,扣减在支付时).
     * 支持使用优惠券(userCouponId 非空时,校验券可用并锁定金额计入 discount_amount).
     */
    OrderCreateResp createOrder(OrderCreateReq req);

    /**
     * 查询订单详情(含明细列表 + 状态中文描述).
     */
    OrderResp getOrder(Long orderId);

    /**
     * 分页查询订单列表(多条件过滤).
     */
    PageResp<OrderListItemResp> listOrders(OrderQueryReq req);

    /**
     * 修改订单(部分更新:备注,支付方式等非关键字段).
     */
    OrderUpdateResp updateOrder(Long orderId, OrderUpdateReq req);

    /**
     * 删除订单(逻辑删除,仅 PENDING/CLOSED 状态可删).
     */
    boolean deleteOrder(Long orderId);

    /**
     * 支付订单(PENDING → PAID).
     * <p>事务内:状态变更 + 库存出库 + 优惠券核销(若有)+ pay_time 填充.
     */
    OrderPayResp payOrder(Long orderId, OrderPayReq req);

    /**
     * 发货(PAID → SHIPPED).
     */
    boolean shipOrder(Long orderId);

    /**
     * 完成订单(SHIPPED → COMPLETED).
     * <p>事务内:状态变更 + 会员积分获取 + 会员汇总更新 + finish_time 填充.
     */
    boolean completeOrder(Long orderId);

    /**
     * 取消订单(PENDING → CLOSED).仅未支付订单可取消.
     */
    boolean cancelOrder(Long orderId);
}
