package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.OrderConvert;
import com.retail.business.convert.OrderItemConvert;
import com.retail.business.dto.req.OrderCreateReq;
import com.retail.business.dto.req.OrderItemReq;
import com.retail.business.dto.req.OrderPayReq;
import com.retail.business.dto.req.OrderQueryReq;
import com.retail.business.dto.req.OrderUpdateReq;
import com.retail.business.dto.resp.OrderCreateResp;
import com.retail.business.dto.resp.OrderItemResp;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.dto.resp.OrderPayResp;
import com.retail.business.dto.resp.OrderResp;
import com.retail.business.dto.resp.OrderUpdateResp;
import com.retail.business.dto.resp.UserCouponResp;
import com.retail.business.entity.Member;
import com.retail.business.entity.OrderInfo;
import com.retail.business.entity.OrderItem;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.ProductSku;
import com.retail.business.enums.OrderChannel;
import com.retail.business.enums.OrderStatus;
import com.retail.business.enums.OrderType;
import com.retail.business.enums.PayType;
import com.retail.business.enums.PointsBizType;
import com.retail.business.enums.StockBizType;
import com.retail.business.enums.SkuStatus;
import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.CouponType;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.mapper.OrderItemMapper;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductSkuMapper;
import com.retail.business.service.OrderService;
import com.retail.business.service.PointsService;
import com.retail.business.service.StockService;
import com.retail.business.service.UserCouponService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.tenant.TenantContext;
import com.retail.rbac.entity.SysStore;
import com.retail.rbac.mapper.SysStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务实现.
 * <p>
 * 订单主表 {@code order_info} 为逻辑删除表(继承 {@link BaseServiceImpl} 复用 delete_at/delete_by 审计填充);
 * 订单明细 {@code order_item} 为物理删除表(仅 created_at/create_by).
 * tenant_id / store_id 由多租户 / 门店拦截器自动注入,代码中不主动赋值.
 * <p>
 * <b>跨模块联动</b>:
 * <ul>
 *   <li>订单创建 → 库存出库({@link StockService#outbound}):内部系统创建即付款,无独立支付环节</li>
 *   <li>订单完成 → 会员积分获取({@link PointsService#earn})+ 会员汇总更新({@link MemberMapper#incTotalOrders})</li>
 *   <li>退款审核通过 → 退券 / 退积分 / 库存回滚(由 {@code RefundServiceImpl.auditRefund} 编排)</li>
 * </ul>
 * <p>
 * <b>内部系统订单流程</b>:订单创建即 PAID(payTime/payType 在创建时写入 + 库存即时出库),
 * 无 PENDING 待付款状态.{@link #payOrder} 已废弃保留兼容,前端不再调用.
 * <p>
 * <b>优惠券核销说明</b>:{@link OrderInfo} 实体未设计 userCouponId 字段,无法在支付阶段回溯创建时传入的券.
 * 故优惠券校验 + 折扣计算 + 核销({@link UserCouponService#use})统一在 {@link #createOrder} 完成,
 * 核销时将 orderId/orderNo 写入 user_coupon,退款时 {@link UserCouponService#refundByOrder} 据此退券.
 */
@Slf4j
@Service
public class OrderServiceImpl extends BaseServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderItemMapper orderItemMapper;
    private final OrderConvert orderConvert;
    private final OrderItemConvert orderItemConvert;
    private final StockService stockService;
    private final UserCouponService userCouponService;
    private final PointsService pointsService;
    private final MemberMapper memberMapper;
    private final ProductInfoMapper productInfoMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SysStoreMapper sysStoreMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 OrderInfoMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * SysStoreMapper 为 rbac 包的 Mapper(sys_store 在 ignore-tables 中,拦截器不自动注入 tenant_id),
     * Service 层只注入 Mapper 不注入 SysStoreService,避免跨模块 Service 循环依赖(用户硬约束).
     */
    public OrderServiceImpl(OrderItemMapper orderItemMapper,
                            OrderConvert orderConvert,
                            OrderItemConvert orderItemConvert,
                            StockService stockService,
                            UserCouponService userCouponService,
                            PointsService pointsService,
                            MemberMapper memberMapper,
                            ProductInfoMapper productInfoMapper,
                            ProductSkuMapper productSkuMapper,
                            SysStoreMapper sysStoreMapper) {
        this.orderItemMapper = orderItemMapper;
        this.orderConvert = orderConvert;
        this.orderItemConvert = orderItemConvert;
        this.stockService = stockService;
        this.userCouponService = userCouponService;
        this.pointsService = pointsService;
        this.memberMapper = memberMapper;
        this.productInfoMapper = productInfoMapper;
        this.productSkuMapper = productSkuMapper;
        this.sysStoreMapper = sysStoreMapper;
    }

    /**
     * 创建订单(含明细列表).内部系统创建即付款,状态初始化为 PAID.
     * <p>流程:校验明细与商品 → 计算明细金额与快照 → 优惠券校验与折扣计算 → 落主表(PAID + payTime)→
     * 落明细 → 核销优惠券 → 库存出库(按明细逐条扣减可用库存).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateResp createOrder(OrderCreateReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new ParamException("订单明细不能为空");
        }

        // 1. 校验商品存在 + 计算明细金额与快照(productName/category/skuCode/skuSpec/unitPrice/subtotal/costPrice)
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>(req.getItems().size());
        for (OrderItemReq itemReq : req.getItems()) {
            if (itemReq.getProductId() == null) {
                throw new ParamException("商品ID不能为空");
            }
            if (itemReq.getQty() == null || itemReq.getQty() <= 0) {
                throw new ParamException("购买数量必须大于0");
            }
            ProductInfo product = productInfoMapper.selectById(itemReq.getProductId());
            if (product == null) {
                throw new ParamException("商品不存在或已下架");
            }
            // B-26 修复:多规格商品(存在在售 SKU)下单必须选择规格.
            // 多规格商品的库存按 SKU 维度挂在 product_stock.sku_id 上,若下单未带 sku_id,
            // 支付出库时 selectByProductAndSku(productId, null) 命中 0 行,getOrCreateStock 会
            // 建空账户(available=0) 导致 outbound 抛"可用库存不足"并回滚整个支付事务.
            // 单规格商品(无在售 SKU)库存挂在 sku_id=NULL,sku_id=null 是正确的,不受此校验影响.
            if (itemReq.getSkuId() == null) {
                Long onShelfSkuCount = productSkuMapper.selectCount(
                        new LambdaQueryWrapper<ProductSku>()
                                .eq(ProductSku::getProductId, itemReq.getProductId())
                                .eq(ProductSku::getStatus, SkuStatus.ON_SHELF));
                if (onShelfSkuCount != null && onShelfSkuCount > 0) {
                    throw new ParamException("该商品有多种规格，请选择具体规格后再下单");
                }
            }

            OrderItem item = new OrderItem();
            item.setProductId(itemReq.getProductId());
            item.setQty(itemReq.getQty());
            item.setProductName(product.getName());          // 商品名快照
            item.setCategory(product.getCategory());         // 分类快照

            // 单价优先取请求传入;否则从 SKU/商品取价;同时填充 SKU 快照与成本价快照
            BigDecimal unitPrice = itemReq.getUnitPrice();
            BigDecimal costPrice = product.getCost();
            if (itemReq.getSkuId() != null) {
                ProductSku sku = productSkuMapper.selectById(itemReq.getSkuId());
                if (sku == null) {
                    throw new ParamException("所选商品规格不存在，请重新选择");
                }
                item.setSkuId(itemReq.getSkuId());
                item.setSkuCode(sku.getSkuCode());           // SKU编码快照
                item.setSkuSpec(sku.getSkuName());           // 规格描述快照
                if (unitPrice == null) {
                    unitPrice = sku.getPrice();
                }
                if (sku.getCost() != null) {
                    costPrice = sku.getCost();               // 成本价快照(优先取 SKU 成本)
                }
            }
            if (unitPrice == null) {
                unitPrice = product.getPrice();
            }
            if (unitPrice == null) {
                throw new ParamException("该商品价格信息异常，请联系管理员");
            }
            item.setUnitPrice(unitPrice);
            item.setCostPrice(costPrice);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQty()));
            item.setSubtotal(subtotal);
            totalAmount = totalAmount.add(subtotal);
            orderItems.add(item);
        }

        // 2. 优惠券校验 + 折扣计算(OrderInfo 无 userCouponId 字段,核销在创建时即完成)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (req.getUserCouponId() != null) {
            UserCouponResp coupon = userCouponService.getUserCoupon(req.getUserCouponId());
            if (coupon == null) {
                throw new ParamException("优惠券不存在");
            }
            // 统一:Integer status → EnumUtil.fromCode 转 CouponStatus 枚举比较,避免 "unused".equals(Integer)
            // 的 Unlikely argument type(该写法 String/Integer 恒不等,会把所有有效券都误判为"不可用")
            if (!CouponStatus.UNUSED.equals(EnumUtil.fromCode(CouponStatus.class, coupon.getStatus()))) {
                throw new ParamException("优惠券不可用");
            }
            if (coupon.getExpireTime() != null && coupon.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new ParamException("优惠券已过期");
            }
            discountAmount = calcDiscount(coupon, totalAmount);
        }

        // 3. 构建订单主表:toEntity 同名字段自动映射,差异字段手动 setter
        //    内部系统创建即付款:status=PAID + payTime=now + payType 取请求值(默认 cash 现金)
        OrderInfo order = orderConvert.toEntity(req);
        String orderNo = generateOrderNo();
        order.setOrderNo(orderNo);
        order.setOrderTime(req.getOrderTime() != null ? req.getOrderTime() : LocalDateTime.now());
        order.setStatus(OrderStatus.PAID);
        // orderType/channel/payType 由 MapStruct toEntity 经 EnumConverter 自动完成 Integer→枚举映射,此处仅兜底默认值
        if (order.getOrderType() == null) {
            order.setOrderType(OrderType.NORMAL);
        }
        if (order.getChannel() == null) {
            order.setChannel(OrderChannel.ONLINE);
        }
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(totalAmount.subtract(discountAmount));
        order.setRefundAmount(BigDecimal.ZERO);
        // 内部系统:创建即付款,记录支付方式与支付时间(库存出库在落明细后执行)
        order.setPayType(order.getPayType() != null ? order.getPayType() : PayType.CASH);
        order.setPayTime(LocalDateTime.now());

        // B-24 修复:平台管理员(无 session tenantId)下单时,TenantInterceptor.ignoreTable 对所有表返回 true
        // 跳过 tenant_id 自动注入,导致 order_info.tenant_id 无值触发 NOT NULL 约束.
        // 由前端传入 appStore.currentTenantId(顶栏已选租户),后端手动设到订单与明细实体上.
        // 租户用户走拦截器自动注入,不进入此分支.
        if (LoginUserHolder.isPlatformAdmin()) {
            if (req.getTenantId() == null) {
                throw new ParamException("请先选择门店后再下单");
            }
            order.setTenantId(req.getTenantId());
            // B-28: 同步注入 TenantContext,使后续 userCouponService.use / stockService 等跨模块调用
            // 的 INSERT(user_coupon 更新条件,stock_movement 等)正确带 tenant_id
            TenantContext.setTenantId(req.getTenantId().toString());
        }

        // 4. 会员冗余 memberName(散客 memberId 为空跳过)
        if (req.getMemberId() != null) {
            Member member = memberMapper.selectById(req.getMemberId());
            if (member != null) {
                order.setMemberName(member.getName());
            }
        }

        // 5. 落订单主表
        save(order);

        // 6. 落订单明细(回填 orderId/orderNo + B-24 admin 手动注入 tenantId)
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            item.setOrderNo(orderNo);
            // B-24:平台管理员明细同样需手动设 tenantId(拦截器对 admin 跳过注入)
            if (LoginUserHolder.isPlatformAdmin()) {
                item.setTenantId(req.getTenantId());
            }
            orderItemMapper.insert(item);
        }

        // 7. 优惠券核销:将 orderId/orderNo 写入 user_coupon,退款时 refundByOrder 据此退券
        if (req.getUserCouponId() != null) {
            userCouponService.use(req.getUserCouponId(), order.getId(), orderNo);
        }

        // 8. 跨模块联动-库存出库:内部系统创建即付款,循环明细逐条扣减可用库存
        //    (原 payOrder 出库逻辑迁移至此,orderItems 已含 productId/skuId/qty 快照)
        Long storeId = order.getStoreId();
        for (OrderItem item : orderItems) {
            stockService.outbound(item.getProductId(), item.getSkuId(), storeId,
                    item.getQty(), StockBizType.ORDER, orderNo, "订单创建出库");
        }

        log.info("创建订单(创建即付款) orderNo={} memberId={} itemCount={} total={} discount={} pay={} payType={} couponId={} channel={}",
                orderNo, req.getMemberId(), orderItems.size(), totalAmount, discountAmount,
                order.getPayAmount(), order.getPayType(), req.getUserCouponId(), order.getChannel());

        OrderCreateResp resp = new OrderCreateResp();
        resp.setSuccess(true);
        resp.setMessage("订单创建成功");
        resp.setOrderId(order.getId());
        resp.setOrderNo(orderNo);
        resp.setPayAmount(order.getPayAmount());
        return resp;
    }

    /**
     * 查询订单详情(含明细列表 + 状态中文描述).
     */
    @Override
    public OrderResp getOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        OrderResp resp = orderConvert.toResp(order);
        resp.setStatusDesc(order.getStatus() != null ? order.getStatus().getDesc() : null);
        // 查订单明细并填充到 resp
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<OrderItemResp> itemResps = orderItemConvert.toRespList(items);
        resp.setItems(itemResps);
        return resp;
    }

    /**
     * 分页查询订单列表(多条件过滤 + 状态描述 + 明细数).
     */
    @Override
    public PageResp<OrderListItemResp> listOrders(OrderQueryReq req) {
        if (req == null) {
            req = new OrderQueryReq();
        }

        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        if (req.getStatus() != null) {
            wrapper.eq(OrderInfo::getStatus, EnumUtil.fromCode(OrderStatus.class, req.getStatus()));
        }
        if (StrUtil.isNotBlank(req.getOrderNo())) {
            wrapper.like(OrderInfo::getOrderNo, req.getOrderNo());
        }
        if (req.getMemberId() != null) {
            wrapper.eq(OrderInfo::getMemberId, req.getMemberId());
        }
        if (StrUtil.isNotBlank(req.getMemberName())) {
            wrapper.like(OrderInfo::getMemberName, req.getMemberName());
        }
        if (req.getOrderType() != null) {
            wrapper.eq(OrderInfo::getOrderType, EnumUtil.fromCode(OrderType.class, req.getOrderType()));
        }
        if (req.getChannel() != null) {
            wrapper.eq(OrderInfo::getChannel, EnumUtil.fromCode(OrderChannel.class, req.getChannel()));
        }
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());
        if (start != null) {
            wrapper.ge(OrderInfo::getOrderTime, start);
        }
        if (end != null) {
            wrapper.le(OrderInfo::getOrderTime, end);
        }
        // 实付金额区间过滤(含边界)
        if (req.getMinAmount() != null) {
            wrapper.ge(OrderInfo::getPayAmount, req.getMinAmount());
        }
        if (req.getMaxAmount() != null) {
            wrapper.le(OrderInfo::getPayAmount, req.getMaxAmount());
        }
        // 支付方式过滤(Integer code → PayType 枚举)
        if (req.getPayType() != null) {
            wrapper.eq(OrderInfo::getPayType, EnumUtil.fromCode(PayType.class, req.getPayType()));
        }
        // 门店显示过滤(仅平台管理员显式传参时生效)
        if (req.getStoreId() != null) {
            wrapper.eq(OrderInfo::getStoreId, req.getStoreId());
        }
        // 商品维度过滤:经 order_item 商品名快照反查订单ID集合,再对主表 IN 过滤
        if (StrUtil.isNotBlank(req.getProductName())) {
            List<Long> orderIds = orderItemMapper.selectList(
                            new LambdaQueryWrapper<OrderItem>()
                                    .like(OrderItem::getProductName, req.getProductName())
                                    .select(OrderItem::getOrderId))
                    .stream().map(OrderItem::getOrderId).collect(Collectors.toList());
            if (orderIds.isEmpty()) {
                return new PageResp<>(Collections.emptyList(), 0L, 1, 1);
            }
            wrapper.in(OrderInfo::getId, orderIds);
        }
        // 会员手机号过滤:先反查 member 得会员ID集合,再对主表 IN 过滤
        if (StrUtil.isNotBlank(req.getMemberPhone())) {
            List<Long> memberIds = memberMapper.selectList(
                            new LambdaQueryWrapper<Member>()
                                    .like(Member::getPhone, req.getMemberPhone())
                                    .select(Member::getId))
                    .stream().map(Member::getId).collect(Collectors.toList());
            if (memberIds.isEmpty()) {
                return new PageResp<>(Collections.emptyList(), 0L, 1, 1);
            }
            if (req.getMemberId() != null) {
                wrapper.eq(OrderInfo::getMemberId, req.getMemberId());
            } else {
                wrapper.in(OrderInfo::getMemberId, memberIds);
            }
        }
        wrapper.orderByDesc(OrderInfo::getId);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<OrderInfo> pageObj = PageContextHolder.get();
        IPage<OrderInfo> result = this.baseMapper.selectPage(pageObj, wrapper);

        // 转化并填充差异字段:statusDesc + itemCount + storeName
        // storeId 由 MapStruct 同名自动映射(OrderInfo.storeId → OrderListItemResp.storeId)
        List<OrderListItemResp> items = orderConvert.toListItemList(result.getRecords());

        // 批量查询门店名称:收集非空 storeId,一次性 selectBatchIds 查 sys_store,构建 ID→名称映射.
        // sys_store 在 ignore-tables 中(拦截器不自动注入 tenant_id),selectBatchIds 走 BaseMapper
        // 默认追加 deleted=0(@TableLogic),安全.Service 层只注入 SysStoreMapper,不注入 SysStoreService.
        Set<Long> storeIds = items.stream()
                .map(OrderListItemResp::getStoreId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> storeNameMap = storeIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : sysStoreMapper.selectBatchIds(storeIds).stream()
                .collect(Collectors.toMap(SysStore::getId, SysStore::getStoreName));

        for (OrderListItemResp i : items) {
            i.setStatusDesc(i.getStatus() != null ? EnumUtil.fromCode(OrderStatus.class, Integer.valueOf(i.getStatus())).getDesc() : null);
            // storeName 填充:storeId 为 null(租户级汇总订单)时保留 null,前端兜底显示"租户中心仓"
            if (i.getStoreId() != null) {
                i.setStoreName(storeNameMap.get(i.getStoreId()));
            }
            Long count = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, i.getId()));
            i.setItemCount(count == null ? 0 : count.intValue());
        }
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    /**
     * 修改订单(部分更新:备注,收货人信息,null 跳过).
     * <p>内部系统订单创建即 PAID,无 PENDING 待付款状态,故不再支持修改支付方式
     * (payType 在创建时已确定).OrderUpdateReq.payType 字段保留兼容但后端忽略.
     * <p>收货字段(receiverName/receiverPhone/receiverAddress)供 Agent 工具 order:update 改收货用,
     * 仅对非 null 字段更新,未传的字段保持不变.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderUpdateResp updateOrder(Long orderId, OrderUpdateReq req) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        boolean changed = false;
        if (req.getRemark() != null) {
            order.setRemark(req.getRemark());
            changed = true;
        }
        if (req.getReceiverName() != null) {
            order.setReceiverName(req.getReceiverName());
            changed = true;
        }
        if (req.getReceiverPhone() != null) {
            order.setReceiverPhone(req.getReceiverPhone());
            changed = true;
        }
        if (req.getReceiverAddress() != null) {
            order.setReceiverAddress(req.getReceiverAddress());
            changed = true;
        }
        if (changed) {
            updateById(order);
        }
        OrderUpdateResp resp = new OrderUpdateResp();
        resp.setSuccess(true);
        resp.setMessage("订单更新成功");
        resp.setUpdated(changed ? 1L : 0L);
        return resp;
    }

    /**
     * 删除订单(逻辑删除,仅 PENDING/CLOSED 状态可删).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        if (!OrderStatus.PENDING.equals(order.getStatus()) && !OrderStatus.CLOSED.equals(order.getStatus())) {
            throw new ParamException("仅待付款或已关闭订单可删除");
        }
        // removeById 由 BaseServiceImpl 处理 deleteAt/deleteBy 审计填充
        boolean result = removeById(orderId);
        log.warn("订单删除 orderNo={} status=deleted", order.getOrderNo());
        return result;
    }

    /**
     * 支付订单(PENDING → PAID).
     * <p><b>已废弃</b>:内部系统订单创建即 PAID({@link #createOrder} 已完成状态变更 + payTime 填充 + 库存出库),
     * 不再存在 PENDING 待付款状态,前端已移除「去支付」按钮.方法保留仅作向后兼容,
     * 若被调用对已 PAID 订单会抛「仅待付款订单可支付」异常.
     */
    @Deprecated
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPayResp payOrder(Long orderId, OrderPayReq req) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        // B-28: admin 支付租户级订单时,注入 TenantContext 使 stock_movement 等后续 INSERT 带 tenant_id
        setupTenantContextForAdmin(order);
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new ParamException("仅待付款订单可支付");
        }
        if (!OrderStatus.canTransit(OrderStatus.PENDING, OrderStatus.PAID)) {
            throw new ParamException("订单状态不允许支付");
        }
        if (req != null && req.getPayType() != null) {
            order.setPayType(EnumUtil.fromCode(PayType.class, req.getPayType()));
        }
        order.setStatus(OrderStatus.PAID);
        order.setPayTime(LocalDateTime.now());
        updateById(order);

        // 跨模块联动-库存出库:循环订单明细逐条扣减可用库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        Long storeId = order.getStoreId();
        for (OrderItem item : items) {
            stockService.outbound(item.getProductId(), item.getSkuId(), storeId,
                    item.getQty(), StockBizType.ORDER, order.getOrderNo(), "订单支付出库");
        }

        log.info("订单支付 orderNo={} payType={} payAmount={} status=paid itemCount={}",
                order.getOrderNo(), order.getPayType(), order.getPayAmount(), items.size());

        OrderPayResp resp = new OrderPayResp();
        resp.setSuccess(true);
        resp.setMessage("支付成功");
        resp.setOrderId(orderId);
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(OrderStatus.PAID.getCode());
        resp.setPayTime(order.getPayTime());
        return resp;
    }

    /**
     * 发货(PAID → SHIPPED).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean shipOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        if (!OrderStatus.PAID.equals(order.getStatus())) {
            throw new ParamException("仅已付款订单可发货");
        }
        if (!OrderStatus.canTransit(OrderStatus.PAID, OrderStatus.SHIPPED)) {
            throw new ParamException("订单状态不允许发货");
        }
        boolean result = baseMapper.markStatus(orderId, OrderStatus.SHIPPED.getCode(), null) > 0;
        log.info("订单发货 orderNo={} status=shipped", order.getOrderNo());
        return result;
    }

    /**
     * 完成订单(SHIPPED → COMPLETED).
     * <p>事务内:状态变更 + finish_time 填充 + 会员积分获取(1元=1积分)+ 会员汇总更新.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        // B-28: admin 完成租户级订单时,注入 TenantContext 使 points_log 等后续 INSERT 带 tenant_id
        setupTenantContextForAdmin(order);
        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            throw new ParamException("仅已发货订单可完成");
        }
        if (!OrderStatus.canTransit(OrderStatus.SHIPPED, OrderStatus.COMPLETED)) {
            throw new ParamException("订单状态不允许完成");
        }

        // 跨模块联动-积分获取:1元=1积分(按实付金额取整)
        if (order.getMemberId() != null && order.getPayAmount() != null) {
            int points = order.getPayAmount().intValue();
            if (points > 0) {
                pointsService.earn(order.getMemberId(), points, PointsBizType.ORDER, order.getOrderNo());
            }
        }
        // 跨模块联动-会员汇总:累计订单数 +1,累计消费金额累加,最后下单时间更新
        if (order.getMemberId() != null) {
            BigDecimal amount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
            LocalDateTime orderTime = order.getOrderTime() == null ? LocalDateTime.now() : order.getOrderTime();
            memberMapper.incTotalOrders(order.getMemberId(), amount, orderTime);
        }

        boolean result = baseMapper.markStatus(orderId, OrderStatus.COMPLETED.getCode(), LocalDateTime.now()) > 0;
        log.info("订单完成 orderNo={} status=completed memberId={} points={}",
                order.getOrderNo(), order.getMemberId(),
                (order.getMemberId() != null && order.getPayAmount() != null) ? order.getPayAmount().intValue() : 0);
        return result;
    }

    /**
     * 取消订单(PENDING → CLOSED).仅未支付订单可取消.
     * <p>优惠券在 createOrder 时已核销(used),取消时退券(used→refunded).
     * 退券后券不可再用,需运营重新发放.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new ParamException("仅待付款订单可取消");
        }
        if (!OrderStatus.canTransit(OrderStatus.PENDING, OrderStatus.CLOSED)) {
            throw new ParamException("订单状态不允许取消");
        }
        boolean result = baseMapper.markStatus(orderId, OrderStatus.CLOSED.getCode(), null) > 0;
        // 跨模块联动-退券:取消订单时释放已核销的优惠券(used→refunded)
        if (result) {
            userCouponService.refundByOrder(orderId);
        }
        log.warn("订单取消 orderNo={} status=closed", order.getOrderNo());
        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * B-28: 平台管理员操作租户级订单时,将订单的 tenant_id 注入 TenantContext.
     * <p>TenantInterceptor.ignoreTable 对 admin + 非空 TenantContext 返回 false,
     * 使后续 stock_movement / points_log 等 INSERT 正确带 tenant_id(避免 NOT NULL 约束违反).
     * 租户用户不进入此方法(已有 session tenantId,拦截器正常注入).
     * TenantContext 为 ThreadLocal,由 GlobalReqInterceptor.afterCompletion 在请求结束时清理.
     */
    private void setupTenantContextForAdmin(OrderInfo order) {
        if (LoginUserHolder.isPlatformAdmin() && order.getTenantId() != null) {
            TenantContext.setTenantId(order.getTenantId().toString());
        }
    }

    /** 生成订单号:yyyyMMddHHmmss + 4位随机数 */
    private String generateOrderNo() {
        return ORDER_NO_FMT.format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 计算优惠券抵扣金额.
     * <ul>
     *   <li>FULLCUT 满减 / CASH 代金券:需满足 threshold 门槛,抵扣 faceValue(不超过订单总额)</li>
     *   <li>DISCOUNT 折扣券:faceValue 为折扣率(如 8.5 表示 85折),抵扣 = 总额 × (1 - 折扣率)</li>
     * </ul>
     * <p>统一:couponType Integer code → EnumUtil.fromCode 转 CouponType 枚举比较,避免字符串
     * 语义码("fullcut")与 MapStruct 默认枚举 name()(大写)大小写不一致导致抵扣恒为 0.
     */
    private BigDecimal calcDiscount(UserCouponResp coupon, BigDecimal totalAmount) {
        if (coupon == null || totalAmount == null || coupon.getFaceValue() == null) {
            return BigDecimal.ZERO;
        }
        CouponType type = EnumUtil.fromCode(CouponType.class, coupon.getCouponType());
        BigDecimal faceValue = coupon.getFaceValue();
        if (CouponType.FULLCUT.equals(type) || CouponType.CASH.equals(type)) {
            if (coupon.getThreshold() != null && totalAmount.compareTo(coupon.getThreshold()) < 0) {
                throw new ParamException("订单金额未达优惠券使用门槛");
            }
            return faceValue.min(totalAmount);
        }
        if (CouponType.DISCOUNT.equals(type)) {
            // faceValue 如 8.5 → 折扣率 0.85 → 抵扣 = 总额 × (1 - 0.85)
            BigDecimal discountRate = faceValue.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            return totalAmount.multiply(BigDecimal.ONE.subtract(discountRate))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /** 起始时间:仅日期时取当天 00:00:00 */
    private LocalDateTime parseStart(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            }
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            throw new ParamException("日期格式错误: " + s);
        }
    }

    /** 结束时间:仅日期时取当天 23:59:59.999999999 */
    private LocalDateTime parseEnd(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            throw new ParamException("日期格式错误: " + s);
        }
    }
}
