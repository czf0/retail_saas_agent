package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.OrderActionToolReq;
import com.retail.business.dto.req.OrderCreateReq;
import com.retail.business.dto.req.OrderDetailToolReq;
import com.retail.business.dto.req.OrderQueryReq;
import com.retail.business.dto.req.OrderQueryToolReq;
import com.retail.business.dto.req.OrderUpdateReq;
import com.retail.business.dto.resp.OrderCreateResp;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.dto.resp.OrderResp;
import com.retail.business.dto.resp.OrderUpdateResp;
import com.retail.business.entity.OrderInfo;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.service.OrderService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;

/**
 * 订单业务 Agent 工具服务 (business="order").
 * <p>
 * 聚合订单域的工具方法, 复用 {@link OrderService} 现有业务逻辑:
 * <ul>
 *   <li>{@code order:query}   — 分页查询订单列表 (只读, 多条件过滤);</li>
 *   <li>{@code order:detail}  — 查询订单详情 (只读, 含明细列表);</li>
 *   <li>{@code order:create}  — 创建订单 (破坏性, HITL 审批);</li>
 *   <li>{@code order:ship}    — 发货 (破坏性, HITL 审批);</li>
 *   <li>{@code order:complete}— 完成订单 (破坏性, HITL 审批);</li>
 *   <li>{@code order:cancel}  — 取消订单 (破坏性, HITL 审批).</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:
 * <ul>
 *   <li>query → business:order:query (对齐 OrderController.list @SaCheckPermission);</li>
 *   <li>detail → business:order:query (对齐 OrderController.detail @SaCheckPermission);</li>
 *   <li>create → business:order:add (对齐 OrderController.create @SaCheckPermission);</li>
 *   <li>ship/complete/cancel → business:order:edit (对齐 OrderController 各状态变更 @SaCheckPermission).</li>
 * </ul>
 * <p>
 * 注意: payOrder 已废弃 (内部系统订单流程 createOrder 即设 PAID 状态), 不封装为工具.
 */
@AgentToolService(business = "order")
public class OrderAgentToolService {

    private final OrderService orderService;
    private final OrderInfoMapper orderInfoMapper;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public OrderAgentToolService(OrderService orderService, OrderInfoMapper orderInfoMapper) {
        this.orderService = orderService;
        this.orderInfoMapper = orderInfoMapper;
    }

    /**
     * 解析订单ID:优先使用传入的 orderId;否则按 orderNo 反查订单.
     * <p>
     * 业务人员通常只掌握订单号,不掌握内部订单ID,故提供按订单号定位的入口.
     * 反查要求唯一命中(订单号租户内唯一),否则抛出 {@link ParamException} 提示.
     *
     * @param orderId 订单ID(可空)
     * @param orderNo 订单号(可空)
     * @return 解析后的订单ID
     */
    private Long resolveOrderId(Long orderId, String orderNo) {
        if (orderId != null) {
            return orderId;
        }
        if (StrUtil.isBlank(orderNo)) {
            throw new ParamException("请提供订单ID或订单号");
        }
        // 按订单号反查订单(先查ID再过滤)
        OrderInfo order = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getOrderNo, orderNo));
        if (order == null) {
            throw new ParamException("未找到订单号对应的订单");
        }
        return order.getId();
    }

    /**
     * 分页查询订单列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link OrderService#listOrders}, 对齐 OrderController.list 的 @SaCheckPermission("business:order:query").
     *
     * @param req 查询条件 (orderNo / memberName / memberPhone / status / orderType / channel / payType / productName / 金额区间 / 日期范围 + 分页)
     * @return 订单列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询订单列表。支持按订单号、会员姓名/手机号、商品名称、状态、类型、渠道、支付方式、金额区间、时间范围过滤。可分页。用于回答'最近订单''待发货订单''王五的订单''买过XX商品的订单''金额超500的订单'等问题。",
        outputHint = "返回订单列表，包含订单号、会员、金额、状态、渠道、下单时间。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<OrderListItemResp> query(OrderQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // OrderQueryReq 不承载分页参数(分页由 PageParameterInterceptor 注入 ThreadLocal),业务字段同名复制
            OrderQueryReq queryReq = new OrderQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return orderService.listOrders(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询订单详情 (只读, 含明细列表 + 状态中文描述).
     * <p>
     * 复用 {@link OrderService#getOrder}, 对齐 OrderController.detail 的 @SaCheckPermission("business:order:query").
     *
     * @param req 查询条件 (orderId / orderNo)
     * @return 订单详情 (含明细列表)
     */
    @AgentTool(
        operation = "detail",
        description = "查询订单详情。支持按订单ID或订单号定位订单，返回订单完整信息，包括订单号、会员、金额、状态、支付方式、明细列表（商品、数量、价格）。用于回答'订单XX的详情'。",
        requiredPermission = "business:order:query",
        outputHint = "返回订单详情，包含订单号、会员、金额、状态、支付方式、下单时间、明细列表。明细用 markdown 表格展示，金额保留 2 位小数。"
    )
    public OrderResp detail(OrderDetailToolReq req) {
        Long orderId = resolveOrderId(req.getOrderId(), req.getOrderNo());
        return orderService.getOrder(orderId);
    }

    /**
     * 创建订单 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link OrderService#createOrder}, 对齐 OrderController.create 的 @SaCheckPermission("business:order:add").
     * 内部系统订单流程: createOrder 即设 PAID + payTime + 库存出库 (无 PENDING 状态).
     * 支持散客 (memberId 为空) 和会员订单, 支持使用优惠券.
     *
     * @param req 创建请求 (memberId / items / payType / channel / userCouponId / remark)
     * @return 创建结果 (含订单 ID,订单号,支付时间)
     */
    @AgentTool(
        operation = "create",
        description = "创建订单。需要订单明细列表（商品ID、数量、单价），支持会员订单和散客订单。内部系统创建即付款，状态直接为已支付。可使用优惠券。此操作会创建订单并扣减库存，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:order:add",
        outputHint = "返回创建结果，包含订单ID、订单号、支付时间、总金额。展示为文本，提示用户订单已创建成功。"
    )
    public OrderCreateResp create(OrderCreateReq req) {
        return orderService.createOrder(req);
    }

    /**
     * 发货 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link OrderService#shipOrder}, 对齐 OrderController.ship 的 @SaCheckPermission("business:order:edit").
     * 将订单状态从 PAID 改为 SHIPPED.
     *
     * @param req 操作请求 (orderId / orderNo)
     * @return 发货结果 (true=成功)
     */
    @AgentTool(
        operation = "ship",
        description = "订单发货。将已支付订单状态改为已发货。支持按订单ID或订单号定位订单。仅已支付(PAID)状态的订单可发货。此操作会改变订单状态，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:order:edit",
        outputHint = "返回发货结果，true表示成功。展示为文本，提示用户订单已发货。"
    )
    public boolean ship(OrderActionToolReq req) {
        Long orderId = resolveOrderId(req.getOrderId(), req.getOrderNo());
        return orderService.shipOrder(orderId);
    }

    /**
     * 完成订单 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link OrderService#completeOrder}, 对齐 OrderController.complete 的 @SaCheckPermission("business:order:edit").
     * 将订单状态从 SHIPPED 改为 COMPLETED, 事务内触发会员积分获取 + 会员汇总更新.
     *
     * @param req 操作请求 (orderId / orderNo)
     * @return 完成结果 (true=成功)
     */
    @AgentTool(
        operation = "complete",
        description = "完成订单。将已发货订单状态改为已完成。完成时会触发会员积分获取。支持按订单ID或订单号定位订单。仅已发货(SHIPPED)状态的订单可完成。此操作会改变订单状态并发放积分，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:order:edit",
        outputHint = "返回完成结果，true表示成功。展示为文本，提示用户订单已完成并已发放积分。"
    )
    public boolean complete(OrderActionToolReq req) {
        Long orderId = resolveOrderId(req.getOrderId(), req.getOrderNo());
        return orderService.completeOrder(orderId);
    }

    /**
     * 取消订单 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link OrderService#cancelOrder}, 对齐 OrderController.cancel 的 @SaCheckPermission("business:order:edit").
     * 将订单状态从 PENDING 改为 CLOSED. 仅未支付订单可取消.
     *
     * @param req 操作请求 (orderId / orderNo)
     * @return 取消结果 (true=成功)
     */
    @AgentTool(
        operation = "cancel",
        description = "取消订单。将待支付订单状态改为已关闭。支持按订单ID或订单号定位订单。仅待支付(PENDING)状态的订单可取消。此操作会改变订单状态，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:order:edit",
        outputHint = "返回取消结果，true表示成功。展示为文本，提示用户订单已取消。"
    )
    public boolean cancel(OrderActionToolReq req) {
        Long orderId = resolveOrderId(req.getOrderId(), req.getOrderNo());
        return orderService.cancelOrder(orderId);
    }

    /**
     * 修改订单(部分更新:备注 / 收货人 / 收货电话 / 收货地址,null 字段不更新).
     * <p>
     * 复用 {@link OrderService#updateOrder}, 对齐 OrderController.update 的 @SaCheckPermission("business:order:edit").
     * 支持按订单ID或订单号定位订单.仅允许修改非关键字段,状态变更沿用专用工具(ship/complete/cancel).
     *
     * @param req 修改请求(orderId 或 orderNo 必填 + remark / receiverName / receiverPhone / receiverAddress 任一)
     * @return 修改结果(success / message / updated 行数)
     */
    @AgentTool(
        operation = "update",
        description = "修改订单信息。支持修改订单备注、收货人姓名、收货电话、收货地址。支持按订单ID或订单号定位订单。未提供的字段保持不变。用于回答'把订单XX的备注改成YY''修改订单XX的收货地址为ZZ'等问题。此操作会修改订单信息，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:order:edit",
        outputHint = "返回修改结果，包含 success、message、updated 行数。展示为文本，提示用户订单信息已更新。"
    )
    public OrderUpdateResp update(OrderUpdateReq req) {
        // 由前端或 Agent 用 orderId / orderNo 定位订单(orderInfo 冗余字段,此处仅用于解析)
        Long orderId = resolveOrderId(req.getOrderId(), req.getOrderNo());
        return orderService.updateOrder(orderId, req);
    }
}
