package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.OrderCreateReq;
import com.retail.business.dto.req.OrderPayReq;
import com.retail.business.dto.req.OrderQueryReq;
import com.retail.business.dto.req.OrderUpdateReq;
import com.retail.business.dto.resp.OrderCreateResp;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.dto.resp.OrderPayResp;
import com.retail.business.dto.resp.OrderResp;
import com.retail.business.dto.resp.OrderUpdateResp;
import com.retail.business.service.OrderService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理接口.
 * <p>
 * 路由前缀 /api/v1/orders.订单表为多租户+门店隔离表,tenant_id/store_id 由拦截器自动注入过滤;
 * 权限校验基于 @SaCheckPermission("business:order:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 创建订单.
     */
    @PostMapping
    @SaCheckPermission("business:order:add")
    public R<OrderCreateResp> create(@RequestBody OrderCreateReq req) {
        return R.ok(orderService.createOrder(req));
    }

    /**
     * 分页查询订单列表(多条件过滤).
     */
    @GetMapping
    @SaCheckPermission("business:order:query")
    public R<PageResp<OrderListItemResp>> list(OrderQueryReq req) {
        return R.ok(orderService.listOrders(req));
    }

    /**
     * 查询订单详情(含明细列表).
     */
    @GetMapping("/{orderId:\\d+}")
    @SaCheckPermission("business:order:query")
    public R<OrderResp> detail(@PathVariable Long orderId) {
        return R.ok(orderService.getOrder(orderId));
    }

    /**
     * 修改订单(部分更新).
     */
    @PutMapping("/{orderId:\\d+}")
    @SaCheckPermission("business:order:edit")
    public R<OrderUpdateResp> update(@PathVariable Long orderId,
                                     @RequestBody OrderUpdateReq req) {
        return R.ok(orderService.updateOrder(orderId, req));
    }

    /**
     * 删除订单(逻辑删除,仅 PENDING/CLOSED 可删).
     */
    @DeleteMapping("/{orderId:\\d+}")
    @SaCheckPermission("business:order:remove")
    public R<Boolean> delete(@PathVariable Long orderId) {
        return R.ok(orderService.deleteOrder(orderId));
    }

    /**
     * 支付订单(PENDING → PAID,触发库存出库 + 优惠券核销).
     */
    @PostMapping("/{orderId:\\d+}/pay")
    @SaCheckPermission("business:order:edit")
    public R<OrderPayResp> pay(@PathVariable Long orderId,
                                @RequestBody OrderPayReq req) {
        return R.ok(orderService.payOrder(orderId, req));
    }

    /**
     * 发货(PAID → SHIPPED).
     */
    @PostMapping("/{orderId:\\d+}/ship")
    @SaCheckPermission("business:order:edit")
    public R<Boolean> ship(@PathVariable Long orderId) {
        return R.ok(orderService.shipOrder(orderId));
    }

    /**
     * 完成订单(SHIPPED → COMPLETED,触发积分获取 + 会员汇总更新).
     */
    @PostMapping("/{orderId:\\d+}/complete")
    @SaCheckPermission("business:order:edit")
    public R<Boolean> complete(@PathVariable Long orderId) {
        return R.ok(orderService.completeOrder(orderId));
    }

    /**
     * 取消订单(PENDING → CLOSED).
     */
    @PostMapping("/{orderId:\\d+}/cancel")
    @SaCheckPermission("business:order:edit")
    public R<Boolean> cancel(@PathVariable Long orderId) {
        return R.ok(orderService.cancelOrder(orderId));
    }
}
