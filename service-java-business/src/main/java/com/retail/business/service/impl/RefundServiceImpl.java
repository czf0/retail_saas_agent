package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.RefundConvert;
import com.retail.business.dto.req.RefundAuditReq;
import com.retail.business.dto.req.RefundCreateReq;
import com.retail.business.dto.req.RefundQueryReq;
import com.retail.business.dto.resp.RefundAuditResp;
import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.dto.resp.RefundResp;
import com.retail.business.entity.Member;
import com.retail.business.entity.OrderInfo;
import com.retail.business.entity.OrderItem;
import com.retail.business.entity.OrderRefund;
import com.retail.business.enums.OrderStatus;
import com.retail.business.enums.RefundStatus;
import com.retail.business.enums.RefundType;
import com.retail.business.enums.StockBizType;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.mapper.OrderItemMapper;
import com.retail.business.mapper.OrderRefundMapper;
import com.retail.business.service.PointsService;
import com.retail.business.service.RefundService;
import com.retail.business.service.StockService;
import com.retail.business.service.UserCouponService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 退款服务实现.
 * <p>
 * 退款单 {@code order_refund} 为物理删除表(仅 created_at/updated_at/create_by/update_by 审计字段),
 * 故本类继承 {@link BaseServiceImpl} 仅复用 IService 通用能力(removeById 的逻辑删除增强不会生效,本场景不调用删除).
 * tenant_id / store_id 由多租户 / 门店拦截器自动注入,代码中不主动赋值.
 * <p>
 * <b>退款流程</b>:
 * <ol>
 *   <li>{@link #createRefund}:校验订单可退 + 退款金额合法 → 建退款单(pending)→ 订单标记 refunding</li>
 *   <li>{@link #auditRefund}:审核通过同事务执行退券 / 退积分 / 库存回滚 / 订单 refund_amount 累加,
 *       全额退则订单 refunded,部分退恢复原状态;拒绝则订单回退原状态</li>
 * </ol>
 * <p>
 * <b>原状态恢复说明</b>:退款单实体未记录 original_order_status,且订单无 shipTime 字段,
 * 故拒绝 / 部分退款时按 finishTime 推断:已填充 finishTime → completed,否则回退 paid(无法区分 paid/shipped,
 * 回退 paid 为最安全状态,详见 {@link #inferOriginalStatus}).
 */
@Slf4j
@Service
public class RefundServiceImpl extends BaseServiceImpl<OrderRefundMapper, OrderRefund> implements RefundService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter REFUND_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundConvert refundConvert;
    private final StockService stockService;
    private final UserCouponService userCouponService;
    private final PointsService pointsService;
    private final MemberMapper memberMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 OrderRefundMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * OrderRefundMapper 仅基础 CRUD,订单 refund_amount 累加 / 状态机变更通过 OrderInfoMapper 自定义方法完成.
     */
    public RefundServiceImpl(OrderInfoMapper orderInfoMapper,
                             OrderItemMapper orderItemMapper,
                             RefundConvert refundConvert,
                             StockService stockService,
                             UserCouponService userCouponService,
                             PointsService pointsService,
                             MemberMapper memberMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundConvert = refundConvert;
        this.stockService = stockService;
        this.userCouponService = userCouponService;
        this.pointsService = pointsService;
        this.memberMapper = memberMapper;
    }

    /**
     * 创建退款申请.
     * <p>校验订单状态为 PAID/SHIPPED/COMPLETED,退款金额不超过可退金额(pay_amount - 已退金额);
     * 状态初始化为 PENDING,订单状态同步标记为 REFUNDING.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResp createRefund(RefundCreateReq req) {
        if (req == null || req.getOrderId() == null) {
            throw new ParamException("订单ID不能为空");
        }
        OrderInfo order = orderInfoMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new ParamException("订单不存在");
        }
        // B-28: admin 操作租户级订单退款时,注入 TenantContext 使 order_refund INSERT 带 tenant_id
        setupTenantContextForAdmin(order);
        if (!OrderStatus.REFUNDABLE.contains(order.getStatus())) {
            throw new ParamException("当前订单状态不支持退款: " + order.getStatus().getDesc());
        }

        // 校验退款金额不超过可退金额(pay_amount - 已退金额)
        BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        BigDecimal alreadyRefund = order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount();
        BigDecimal refundable = payAmount.subtract(alreadyRefund);
        BigDecimal refundAmount = req.getRefundAmount();
        // 全额退款:金额取可退余额
        if (req.getRefundType() != null && req.getRefundType() == RefundType.FULL.getCode()) {
            refundAmount = refundable;
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ParamException("退款金额必须大于0");
        }
        if (refundAmount.compareTo(refundable) > 0) {
            throw new ParamException("退款金额超过可退金额: " + refundable);
        }

        // 构建退款单
        OrderRefund refund = new OrderRefund();
        refund.setRefundNo(generateRefundNo());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setMemberId(order.getMemberId());
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值);null 回退 PARTIAL
        refund.setRefundType(req.getRefundType() == null ? RefundType.PARTIAL : EnumUtil.fromCode(RefundType.class, req.getRefundType()));
        refund.setRefundAmount(refundAmount);
        refund.setRefundQty(req.getRefundQty());
        refund.setReason(req.getReason());
        refund.setStatus(RefundStatus.PENDING);
        refund.setApplyTime(LocalDateTime.now());
        save(refund);

        // 同步标记订单为退款中(PAID/SHIPPED/COMPLETED → REFUNDING,canTransit 允许)
        orderInfoMapper.markStatus(order.getId(), OrderStatus.REFUNDING.getCode(), null);

        log.info("创建退款申请 refundNo={} orderNo={} type={} amount={} qty={} reason={}",
                refund.getRefundNo(), order.getOrderNo(), refund.getRefundType(),
                refundAmount, refund.getRefundQty(), refund.getReason());

        RefundResp resp = refundConvert.toResp(refund);
        resp.setStatusDesc(refund.getStatus().getDesc());
        return resp;
    }

    /**
     * 审核退款单.
     * <p>审核通过时事务内执行退款联动:退券 → 退积分 → 库存回滚 → 订单 refund_amount 累加 → 订单状态收尾
     * (全额退改 refunded,部分退恢复原状态);审核拒绝时订单状态回退原状态.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundAuditResp auditRefund(Long refundId, RefundAuditReq req) {
        OrderRefund refund = getById(refundId);
        if (refund == null) {
            throw new ParamException("退款单不存在");
        }
        if (!RefundStatus.PENDING.equals(refund.getStatus())) {
            throw new ParamException("仅待审核退款单可审核");
        }
        if (req == null || req.getResult() == null) {
            throw new ParamException("审核结果不能为空");
        }
        Integer result = req.getResult();

        OrderInfo order = orderInfoMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new ParamException("关联订单不存在");
        }

        if (result == RefundStatus.APPROVED.getCode()) {
            approveRefund(refund, order);
        } else if (result == RefundStatus.REJECTED.getCode()) {
            rejectRefund(refund, order);
        } else {
            throw new ParamException("审核结果无效，取值 2(通过)/3(拒绝)");
        }

        RefundAuditResp resp = new RefundAuditResp();
        resp.setSuccess(true);
        resp.setMessage(result == RefundStatus.APPROVED.getCode() ? "退款审核通过" : "退款审核拒绝");
        resp.setRefundId(refundId);
        resp.setStatus(refund.getStatus() != null ? refund.getStatus().getCode() : null);
        return resp;
    }

    /**
     * 撤销待审核退款单.
     * <p>仅 {@code PENDING} 状态可撤销;撤销后退款单置为 {@code CANCELLED},
     * 关联订单从退款中(REFUNDING)恢复退款前原状态(与审核拒绝一致,按 finishTime 推断).
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResp cancel(Long refundId) {
        OrderRefund refund = getById(refundId);
        if (refund == null) {
            throw new ParamException("退款单不存在");
        }
        if (!RefundStatus.PENDING.equals(refund.getStatus())) {
            throw new ParamException("仅待审核退款单可撤销");
        }
        OrderInfo order = orderInfoMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw new ParamException("关联订单不存在");
        }
        // 撤销:退款单置已撤销,订单从退款中恢复原状态
        refund.setStatus(RefundStatus.CANCELLED);
        updateById(refund);
        orderInfoMapper.markStatus(order.getId(), inferOriginalStatus(order), null);
        log.info("撤销退款单 refundNo={} orderNo={} finalStatus={}",
                refund.getRefundNo(), order.getOrderNo(), inferOriginalStatus(order));

        RefundResp resp = refundConvert.toResp(refund);
        resp.setStatusDesc(refund.getStatus().getDesc());
        return resp;
    }

    /**
     * 查询退款单详情.
     */
    @Override
    public RefundResp getRefund(Long refundId) {
        OrderRefund refund = getById(refundId);
        if (refund == null) {
            throw new ParamException("退款单不存在");
        }
        RefundResp resp = refundConvert.toResp(refund);
        resp.setStatusDesc(refund.getStatus().getDesc());
        return resp;
    }

    /**
     * 分页查询退款单列表(支持 status / orderNo / 申请时间区间过滤).
     * <p>
     * 通过 {@code OrderRefundMapper.selectRefundPage} 的 LEFT JOIN member 一次性带出会员名称,
     * 消除前端展示 memberId 无对应会员名 的数据孤岛.Service 层只调用 Mapper,不注入 MemberService,
     * 避免跨模块 Service 间循环依赖(用户硬约束).
     * <p>
     * tenant_id / store_id 由拦截器自动注入;member 表的 tenant_id 条件进入 LEFT JOIN 的 ON 子句,
     * 不破坏外连接语义,散客退款(member_id 为 NULL)行正常返回.
     */
    @Override
    public PageResp<RefundListItemResp> listRefunds(Integer status, String orderNo,
                                                    String startDate, String endDate) {
        // 解析时间区间(空字符串/非法格式返回 null,SQL 中由 <if> 跳过)
        LocalDateTime start = parseStart(startDate);
        LocalDateTime end = parseEnd(endDate);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectRefundPage 内部由分页插件自动拼接 count + LIMIT,无需手动 selectCount.
        // Page 泛型擦除:PageContextHolder.get() 返回的 Page<?> 可安全转为 Page<RefundListItemResp>
        Page<RefundListItemResp> pageObj = PageContextHolder.get();
        IPage<RefundListItemResp> result = this.baseMapper.selectRefundPage(
                pageObj, status, orderNo, start, end);

        // 填充差异字段:statusDesc(SQL 不便 CASE WHEN 枚举,由 Service 层 RefundStatus.getDesc 填充)
        List<RefundListItemResp> items = result.getRecords();
        items.forEach(i -> {
            RefundStatus rs = EnumUtil.fromCode(RefundStatus.class, i.getStatus());
            i.setStatusDesc(rs != null ? rs.getDesc() : null);
        });
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    @Override
    public PageResp<RefundListItemResp> listRefunds(RefundQueryReq req) {
        if (req == null) {
            req = new RefundQueryReq();
        }
        // 解析时间区间(空字符串/非法格式返回 null,SQL 中由 <if> 跳过)
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());

        // 会员姓名/手机号 → 先反查会员ID集合,再 IN 过滤(先查ID再过滤)
        List<Long> memberIds = new ArrayList<>();
        if (StrUtil.isNotBlank(req.getMemberName()) || StrUtil.isNotBlank(req.getMemberPhone())) {
            LambdaQueryWrapper<Member> mw = new LambdaQueryWrapper<>();
            if (StrUtil.isNotBlank(req.getMemberName())) {
                mw.like(Member::getName, req.getMemberName());
            }
            if (StrUtil.isNotBlank(req.getMemberPhone())) {
                mw.like(Member::getPhone, req.getMemberPhone());
            }
            // 只取 id 列,避免全字段查询
            mw.select(Member::getId);
            memberIds = memberMapper.selectList(mw).stream()
                    .map(Member::getId).collect(Collectors.toList());
            if (memberIds.isEmpty()) {
                return new PageResp<>(Collections.emptyList(), 0L, 1, 1);
            }
        }

        Page<RefundListItemResp> pageObj = PageContextHolder.get();
        IPage<RefundListItemResp> result = this.baseMapper.selectRefundPageByReq(
                pageObj, req.getStatus(), req.getOrderNo(), start, end,
                req.getRefundType(), req.getMinAmount(), req.getMaxAmount(), memberIds);

        List<RefundListItemResp> items = result.getRecords();
        items.forEach(i -> {
            RefundStatus rs = EnumUtil.fromCode(RefundStatus.class, i.getStatus());
            i.setStatusDesc(rs != null ? rs.getDesc() : null);
        });
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 审核通过:执行退款联动.
     * <p>退券 → 退积分 → 库存回滚 → 订单 refund_amount 累加 → 订单状态收尾.
     */
    private void approveRefund(OrderRefund refund, OrderInfo order) {
        // B-28: admin 审核租户级订单退款时,注入 TenantContext 使 points_log / stock_movement 等 INSERT 带 tenant_id
        setupTenantContextForAdmin(order);

        // 退款单状态:approved → refunded,填充退款时间
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setRefundTime(LocalDateTime.now());
        updateById(refund);

        // 1. 退券:将本订单关联的 used 状态 user_coupon 改为 refunded(无券时返回 0,安全)
        userCouponService.refundByOrder(order.getId());

        // 2. 退积分:仅在订单已完成(finishTime 已填充)时才扣减积分.
        // <p>B-27 修复:积分仅在 completeOrder(SHIPPED→COMPLETED)时通过 pointsService.earn 发放,
        //   1元=1积分.若订单仅支付/发货而未完成,会员从未获得积分,此时退款扣减会导致超扣.
        //   故以 finishTime != null 作为「积分已发放」的判据,未完成订单退款跳过退积分.
        boolean pointsRefunded = false;
        if (order.getMemberId() != null && refund.getRefundAmount() != null
                && order.getFinishTime() != null) {
            int pointsToRefund = refund.getRefundAmount().intValue();
            if (pointsToRefund > 0) {
                pointsService.refund(order.getMemberId(), pointsToRefund, refund.getRefundNo());
                pointsRefunded = true;
            }
        }

        // 3. 库存回滚:全额退按各明细剩余可退数量入库;部分退按 refundQty 按明细 FIFO 分摊入库
        rollbackStock(refund, order);

        // 4. 订单 refund_amount 累加(SQL 原子累加,并发安全且校验不超额)
        orderInfoMapper.addRefundAmount(order.getId(), refund.getRefundAmount());

        // 5. 订单状态收尾:累计退款达实付金额 → refunded;否则恢复退款前状态
        BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        BigDecimal totalRefund = (order.getRefundAmount() == null ? BigDecimal.ZERO : order.getRefundAmount())
                .add(refund.getRefundAmount());
        Integer finalStatus;
        if (totalRefund.compareTo(payAmount) >= 0) {
            orderInfoMapper.markStatus(order.getId(), OrderStatus.REFUNDED.getCode(), null);
            finalStatus = OrderStatus.REFUNDED.getCode();
        } else {
            orderInfoMapper.markStatus(order.getId(), inferOriginalStatus(order), null);
            finalStatus = inferOriginalStatus(order);
        }

        log.info("退款审核通过 refundNo={} orderNo={} amount={} pointsRefunded={} finalStatus={}",
                refund.getRefundNo(), order.getOrderNo(), refund.getRefundAmount(), pointsRefunded, finalStatus);
    }

    /**
     * 审核拒绝:退款单置 rejected,订单状态从 refunding 回退原状态.
     */
    private void rejectRefund(OrderRefund refund, OrderInfo order) {
        refund.setStatus(RefundStatus.REJECTED);
        updateById(refund);
        orderInfoMapper.markStatus(order.getId(), inferOriginalStatus(order), null);
        log.info("退款审核拒绝 refundNo={} orderNo={} finalStatus={}",
                refund.getRefundNo(), order.getOrderNo(), inferOriginalStatus(order));
    }

    /**
     * 库存回滚:按退款数量入库.
     * <ul>
     *   <li>全额退款:各明细按剩余可退数量(qty - 已退数量)入库</li>
     *   <li>部分退款:refundQty 按 FIFO 顺序分摊到各明细,直至分配完毕</li>
     *   <li>部分退款且 refundQty 为空(纯金额调整无退货):跳过库存回滚</li>
     * </ul>
     * 同步累加各明细 refund_qty(addRefundQty SQL 原子累加并校验不超额).
     */
    private void rollbackStock(OrderRefund refund, OrderInfo order) {
        boolean isFull = RefundType.FULL.equals(refund.getRefundType());
        int remaining = isFull ? Integer.MAX_VALUE
                : (refund.getRefundQty() != null ? refund.getRefundQty() : 0);
        if (!isFull && remaining <= 0) {
            return; // 纯金额退款, 无退货数量
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        Long storeId = order.getStoreId();
        for (OrderItem item : items) {
            if (!isFull && remaining <= 0) {
                break;
            }
            int itemRefundable = item.getQty() - (item.getRefundQty() == null ? 0 : item.getRefundQty());
            if (itemRefundable <= 0) {
                continue;
            }
            int itemRefundQty = isFull ? itemRefundable : Math.min(itemRefundable, remaining);
            stockService.inbound(item.getProductId(), item.getSkuId(), storeId,
                    itemRefundQty, StockBizType.REFUND, refund.getRefundNo(), "退款入库");
            orderItemMapper.addRefundQty(item.getId(), itemRefundQty);
            if (!isFull) {
                remaining -= itemRefundQty;
            }
        }
    }

    /**
     * 推断订单退款前原状态(拒绝 / 部分退款恢复用).
     * <p>退款单未记录原状态,订单无 shipTime 字段,故:finishTime 已填充 → completed;
     * 否则回退 paid(无法区分 paid/shipped,paid 为最安全状态,可重新发货).
     */
    private Integer inferOriginalStatus(OrderInfo order) {
        if (order.getFinishTime() != null) {
            return OrderStatus.COMPLETED.getCode();
        }
        return OrderStatus.PAID.getCode();
    }

    /**
     * B-28: 平台管理员操作租户级订单时,将订单的 tenant_id 注入 TenantContext.
     * <p>使 TenantInterceptor 不再跳过该表,从而正确注入 tenant_id 到 points_log / stock_movement 等 INSERT.
     * 与 OrderServiceImpl.setupTenantContextForAdmin 逻辑一致.
     */
    private void setupTenantContextForAdmin(OrderInfo order) {
        if (LoginUserHolder.isPlatformAdmin() && order.getTenantId() != null) {
            TenantContext.setTenantId(order.getTenantId().toString());
        }
    }

    /** 生成退款单号:RF + yyyyMMddHHmmss + 4位随机数 */
    private String generateRefundNo() {
        return "RF" + REFUND_NO_FMT.format(LocalDateTime.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
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
