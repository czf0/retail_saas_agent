package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.UserCouponConvert;
import com.retail.business.dto.req.CouponQueryReq;
import com.retail.business.dto.req.CouponReceiveReq;
import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.UserCouponResp;
import com.retail.business.entity.CouponTemplate;
import com.retail.business.entity.UserCoupon;
import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.ValidType;
import com.retail.business.mapper.CouponTemplateMapper;
import com.retail.business.mapper.UserCouponMapper;
import com.retail.business.service.UserCouponService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户优惠券服务实现.
 * <p>
 * tenant_id / store_id 由拦截器自动注入,代码不主动赋值 storeId;
 * 通过注入 CouponTemplateMapper 直接读取模板(避免与 CouponService 形成循环依赖).
 * <p>
 * use / refundByOrder 方法 public 且 {@link Transactional},供订单模块 OrderServiceImpl 跨模块调用.
 */
@Slf4j
@Service
public class UserCouponServiceImpl extends BaseServiceImpl<UserCouponMapper, UserCoupon> implements UserCouponService {

    private final UserCouponConvert userCouponConvert;
    private final CouponTemplateMapper couponTemplateMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 UserCouponMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>couponTemplateMapper 用于 receive 时校验模板状态 / 发放总量 / 每人限领(直接注入 Mapper 而非 CouponService,
     * 遵循铁律 21 避免 Service 间循环依赖);userCouponConvert 用于 UserCoupon → UserCouponResp 转换(枚举自动映射).
     */
    public UserCouponServiceImpl(UserCouponConvert userCouponConvert, CouponTemplateMapper couponTemplateMapper) {
        this.userCouponConvert = userCouponConvert;
        this.couponTemplateMapper = couponTemplateMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResp receive(CouponReceiveReq req) {
        // 加载并校验模板
        CouponTemplate template = couponTemplateMapper.selectById(req.getCouponId());
        if (template == null) {
            throw new ParamException("优惠券模板不存在");
        }
        if (!PromotionStatus.ACTIVE.equals(template.getStatus())) {
            throw new ParamException("优惠券模板已停用，不可领取");
        }
        // 校验发放总量(totalCount=0 不限)
        if (template.getTotalCount() != null && template.getTotalCount() != 0
                && template.getIssuedCount() != null
                && template.getIssuedCount() >= template.getTotalCount()) {
            throw new ParamException("优惠券已发放完毕");
        }
        // 校验每人限领(统计该会员历史领取记录)
        int perLimit = template.getPerLimit() == null ? 1 : template.getPerLimit();
        Long userCount = baseMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, req.getCouponId())
                .eq(UserCoupon::getMemberId, req.getMemberId()));
        if (userCount != null && userCount >= perLimit) {
            throw new ParamException("会员已达限领数量");
        }

        LocalDateTime now = LocalDateTime.now();
        // 计算 expire_time:relative=now+valid_days;fixed=valid_end
        LocalDateTime expireTime;
        if (ValidType.RELATIVE.equals(template.getValidType())) {
            expireTime = now.plusDays(template.getValidDays());
        } else {
            expireTime = template.getValidEnd();
        }

        // 构造 user_coupon 记录(冗余券名/类型/面额/门槛,避免模板修改影响历史券)
        UserCoupon uc = new UserCoupon();
        uc.setCouponId(template.getId());
        uc.setCouponName(template.getName());
        uc.setCouponType(template.getType());
        uc.setMemberId(req.getMemberId());
        uc.setStatus(CouponStatus.UNUSED);
        uc.setFaceValue(template.getFaceValue());
        uc.setThreshold(template.getThreshold());
        uc.setReceiveTime(now);
        uc.setExpireTime(expireTime);
        // storeId / tenantId 不主动赋值,由门店/多租户拦截器自动注入
        baseMapper.insert(uc);
        log.info("用户领取优惠券 userCouponId={} couponId={} memberId={} type={} faceValue={} expireTime={}",
                uc.getId(), uc.getCouponId(), uc.getMemberId(), uc.getCouponType(),
                uc.getFaceValue(), uc.getExpireTime());

        // 同事务内累加模板 issued_count(SQL 级原子累加,避免内存值竞态)
        couponTemplateMapper.update(null, new UpdateWrapper<CouponTemplate>()
                .eq("id", template.getId())
                .setSql("issued_count = issued_count + 1"));

        // 同名字段由 UserCouponConvert 自动映射
        return userCouponConvert.toResp(uc);
    }

    @Override
    public PageResp<UserCouponListItemResp> listUserCoupons(CouponQueryReq req) {
        if (req == null) {
            req = new CouponQueryReq();
        }
        // 通过 LEFT JOIN member 一次性带出会员名称,消除前端数据孤岛.
        // Service 层只调用 Mapper,不注入 MemberService,避免跨模块 Service 循环依赖(用户硬约束).
        // tenant_id / store_id 由拦截器自动注入;member 表 tenant_id 进入 ON 子句,不破坏外连接.
        Page<UserCouponListItemResp> page = PageContextHolder.get();
        IPage<UserCouponListItemResp> result = baseMapper.selectUserCouponPage(
                page, req.getMemberId(),
                req.getStatus(), req.getCouponId(),
                req.getStartDate(), req.getEndDate());

        return new PageResp<>(result.getRecords(), result.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public List<UserCouponListItemResp> listByMember(Long memberId, Integer status) {
        // 调用自定义 Mapper 方法(@Select 注解,参数为空时由 SQL 中 IS NULL 处理)
        List<UserCoupon> records = baseMapper.selectByMemberAndStatus(memberId, status);
        return userCouponConvert.toListItemList(records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponResp use(Long userCouponId, Long orderId, String orderNo) {
        UserCoupon uc = baseMapper.selectById(userCouponId);
        if (uc == null) {
            throw new ParamException("用户优惠券不存在");
        }
        // 校验状态:仅 unused 可核销
        if (!CouponStatus.UNUSED.equals(uc.getStatus())) {
            throw new ParamException("用户优惠券当前状态不可核销：" + uc.getStatus().getDesc());
        }
        // 校验有效期:当前时间必须早于过期时间
        if (uc.getExpireTime() == null || LocalDateTime.now().isAfter(uc.getExpireTime())) {
            throw new ParamException("用户优惠券已过期，不可核销");
        }

        // 使用部分实体 + updateById,仅更新核销相关字段
        UserCoupon update = new UserCoupon();
        update.setId(userCouponId);
        update.setStatus(CouponStatus.USED);
        update.setOrderId(orderId);
        update.setOrderNo(orderNo);
        update.setUsedTime(LocalDateTime.now());
        int rows = baseMapper.updateById(update);
        if (rows <= 0) {
            throw new ParamException("用户优惠券核销失败");
        }

        // 返回更新后的完整记录(含 usedTime/orderId/orderNo)
        UserCoupon refreshed = baseMapper.selectById(userCouponId);
        log.info("核销用户优惠券 userCouponId={} orderId={} orderNo={} memberId={}",
                userCouponId, orderId, orderNo, refreshed.getMemberId());
        return userCouponConvert.toResp(refreshed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer refundByOrder(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        // 单条 UPDATE 批量退券:仅 used 状态券可退,避免重复退
        int rows = baseMapper.update(null, new UpdateWrapper<UserCoupon>()
                .eq("order_id", orderId)
                .eq("status", CouponStatus.USED)
                .set("status", CouponStatus.REFUNDED));
        log.info("订单退券 orderId={} refundedCount={}", orderId, rows);
        return rows;
    }

    @Override
    public UserCouponResp getUserCoupon(Long userCouponId) {
        UserCoupon uc = baseMapper.selectById(userCouponId);
        if (uc == null) {
            throw new ParamException("用户优惠券不存在");
        }
        // 同名字段由 UserCouponConvert 自动映射
        return userCouponConvert.toResp(uc);
    }

    // ========== 过期处理预留接口 ==========
    // 本期不实现定时任务扫描 expired 状态:可后续通过 Spring Scheduling 或 Quartz 调度
    // 调用 UserCouponMapper 批量 UPDATE WHERE status=unused AND expire_time<NOW() SET status=expired
}
