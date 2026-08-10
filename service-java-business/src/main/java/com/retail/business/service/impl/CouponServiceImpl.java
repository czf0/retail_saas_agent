package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.CouponConvert;
import com.retail.business.dto.req.CouponIssueReq;
import com.retail.business.dto.req.CouponTemplateCreateReq;
import com.retail.business.dto.req.CouponTemplateQueryReq;
import com.retail.business.dto.req.CouponTemplateUpdateReq;
import com.retail.business.dto.resp.CouponIssueResp;
import com.retail.business.dto.resp.CouponTemplateListItemResp;
import com.retail.business.dto.resp.CouponTemplateResp;
import com.retail.business.entity.CouponTemplate;
import com.retail.business.entity.UserCoupon;
import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.CouponType;
import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.ValidType;
import com.retail.business.mapper.CouponTemplateMapper;
import com.retail.business.mapper.UserCouponMapper;
import com.retail.business.service.CouponService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.BizException;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 优惠券模板服务实现.
 * <p>
 * tenant_id 由多租户拦截器自动注入,逻辑删除由全局配置管理;
 * 通过注入 UserCouponMapper 直接写 user_coupon 记录(避免与 UserCouponService 形成循环依赖).
 */
@Slf4j
@Service
public class CouponServiceImpl extends BaseServiceImpl<CouponTemplateMapper, CouponTemplate> implements CouponService {

    private final CouponConvert couponConvert;
    private final UserCouponMapper userCouponMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 CouponTemplateMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>userCouponMapper 用于 issue 接口批量写 user_coupon 记录(同事务内原子累加模板 issued_count),
     * 此处直接注入 Mapper 而非 UserCouponService,遵循铁律 21 避免 Service 间循环依赖.
     */
    public CouponServiceImpl(CouponConvert couponConvert, UserCouponMapper userCouponMapper) {
        this.couponConvert = couponConvert;
        this.userCouponMapper = userCouponMapper;
    }

    @Override
    public CouponTemplateResp createTemplate(CouponTemplateCreateReq req) {
        // 入参校验
        validateCreateReq(req);
        // 同名字段由 CouponConvert 自动映射(req→entity)
        CouponTemplate entity = couponConvert.toEntity(req);
        // status 由 Service 赋默认值 active(铁律6:CreateReq 禁 status 字段)
        entity.setStatus(PromotionStatus.ACTIVE);
        // 新模板 issuedCount 从 0 开始(防御性赋值,DB 已有默认值)
        if (entity.getIssuedCount() == null) {
            entity.setIssuedCount(0);
        }
        save(entity);
        log.info("创建优惠券模板 id={} name={} type={} faceValue={} validType={} totalCount={} perLimit={}",
                entity.getId(), entity.getName(), entity.getType(), entity.getFaceValue(),
                entity.getValidType(), entity.getTotalCount(), entity.getPerLimit());
        // 同名字段由 CouponConvert 自动映射
        return couponConvert.toResp(entity);
    }

    /** 创建请求基础校验:必填字段,类型与有效期合法性 */
    private void validateCreateReq(CouponTemplateCreateReq req) {
        if (StrUtil.isBlank(req.getName())) {
            throw new ParamException("券名不能为空");
        }
        if (req.getType() == null) {
            throw new ParamException("类型不能为空");
        }
        CouponType couponType = EnumUtil.fromCode(CouponType.class, req.getType());
        if (!CouponType.FULLCUT.equals(couponType)
                && !CouponType.DISCOUNT.equals(couponType)
                && !CouponType.CASH.equals(couponType)) {
            throw new ParamException("类型非法，仅支持 fullcut/discount/cash");
        }
        if (req.getFaceValue() == null) {
            throw new ParamException("面额不能为空");
        }
        if (req.getValidType() == null) {
            throw new ParamException("有效期类型不能为空");
        }
        ValidType validType = EnumUtil.fromCode(ValidType.class, req.getValidType());
        if (!ValidType.RELATIVE.equals(validType) && !ValidType.FIXED.equals(validType)) {
            throw new ParamException("有效期类型非法，仅支持 relative/fixed");
        }
        if (ValidType.RELATIVE.equals(validType)) {    
            if (req.getValidDays() == null || req.getValidDays() <= 0) {
                throw new ParamException("relative 有效期类型需指定正整数 validDays");
            }
        } else {
            // fixed 模式需起止时间且 start < end
            if (req.getValidStart() == null || req.getValidEnd() == null) {
                throw new ParamException("fixed 有效期类型需指定 validStart 与 validEnd");
            }
            if (!req.getValidStart().isBefore(req.getValidEnd())) {
                throw new ParamException("validStart 必须早于 validEnd");
            }
        }
        if (req.getTotalCount() != null && req.getTotalCount() < 0) {
            throw new ParamException("发放总量不能为负数");
        }
        if (req.getPerLimit() != null && req.getPerLimit() <= 0) {
            throw new ParamException("每人限领必须为正整数");
        }
    }

    @Override
    public PageResp<CouponTemplateListItemResp> listTemplates(Integer status, Integer type, String keyword) {
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (status != null) {
            wrapper.eq(CouponTemplate::getStatus, EnumUtil.fromCode(PromotionStatus.class, status));
        }
        if (type != null) {
            wrapper.eq(CouponTemplate::getType, EnumUtil.fromCode(CouponType.class, type));
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(CouponTemplate::getName, keyword);
        }
        wrapper.orderByDesc(CouponTemplate::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<CouponTemplate> page = PageContextHolder.get();
        IPage<CouponTemplate> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射)
        List<CouponTemplateListItemResp> items = couponConvert.toListItemList(result.getRecords());
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageResp<CouponTemplateListItemResp> listTemplates(CouponTemplateQueryReq req) {
        if (req == null) {
            req = new CouponTemplateQueryReq();
        }
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<>();
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (req.getStatus() != null) {
            wrapper.eq(CouponTemplate::getStatus, EnumUtil.fromCode(PromotionStatus.class, req.getStatus()));
        }
        if (req.getType() != null) {
            wrapper.eq(CouponTemplate::getType, EnumUtil.fromCode(CouponType.class, req.getType()));
        }
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.like(CouponTemplate::getName, req.getKeyword());
        }
        // 面额区间过滤
        if (req.getMinFaceValue() != null) {
            wrapper.ge(CouponTemplate::getFaceValue, req.getMinFaceValue());
        }
        if (req.getMaxFaceValue() != null) {
            wrapper.le(CouponTemplate::getFaceValue, req.getMaxFaceValue());
        }
        // 使用门槛区间过滤
        if (req.getMinThreshold() != null) {
            wrapper.ge(CouponTemplate::getThreshold, req.getMinThreshold());
        }
        if (req.getMaxThreshold() != null) {
            wrapper.le(CouponTemplate::getThreshold, req.getMaxThreshold());
        }
        // 有效期固定起止范围过滤(yyyy-MM-dd)
        if (StrUtil.isNotBlank(req.getValidStart())) {
            wrapper.ge(CouponTemplate::getValidStart, LocalDate.parse(req.getValidStart()).atStartOfDay());
        }
        if (StrUtil.isNotBlank(req.getValidEnd())) {
            wrapper.le(CouponTemplate::getValidEnd, LocalDate.parse(req.getValidEnd()).atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(CouponTemplate::getCreatedAt);

        Page<CouponTemplate> page = PageContextHolder.get();
        IPage<CouponTemplate> result = baseMapper.selectPage(page, wrapper);

        List<CouponTemplateListItemResp> items = couponConvert.toListItemList(result.getRecords());
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public CouponTemplateResp getTemplate(Long couponId) {
        CouponTemplate entity = getById(couponId);
        if (entity == null) {
            throw new ParamException("优惠券模板不存在");
        }
        // 同名字段由 CouponConvert 自动映射
        return couponConvert.toResp(entity);
    }

    @Override
    public CouponTemplateResp updateTemplate(Long couponId, CouponTemplateUpdateReq req) {
        // 使用部分实体 + updateById,避免覆盖其他字段
        CouponTemplate entity = new CouponTemplate();
        entity.setId(couponId);
        boolean hasField = false;
        if (StrUtil.isNotBlank(req.getName())) {
            entity.setName(req.getName());
            hasField = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(PromotionStatus.class, req.getStatus()));
            hasField = true;
        }
        if (req.getTotalCount() != null) {
            if (req.getTotalCount() < 0) {
                throw new ParamException("发放总量不能为负数");
            }
            entity.setTotalCount(req.getTotalCount());
            hasField = true;
        }
        if (req.getPerLimit() != null) {
            if (req.getPerLimit() <= 0) {
                throw new ParamException("每人限领必须为正整数");
            }
            entity.setPerLimit(req.getPerLimit());
            hasField = true;
        }
        if (req.getValidEnd() != null) {
            entity.setValidEnd(req.getValidEnd());
            hasField = true;
        }
        // threshold 可调:使用门槛属于运营参数,调整不影响已发放券的核销金额(faceValue 不变)
        if (req.getThreshold() != null) {
            if (req.getThreshold().signum() < 0) {
                throw new ParamException("使用门槛不能为负数");
            }
            entity.setThreshold(req.getThreshold());
            hasField = true;
        }

        if (hasField) {
            int rows = baseMapper.updateById(entity);
            if (rows <= 0) {
                throw new ParamException("优惠券模板不存在");
            }
            log.info("更新优惠券模板 id={} name={} status={} totalCount={} perLimit={} validEnd={} threshold={}",
                    couponId, req.getName(), req.getStatus(), req.getTotalCount(),
                    req.getPerLimit(), req.getValidEnd(), req.getThreshold());
        }
        // 返回更新后的完整实体
        return getTemplate(couponId);
    }

    @Override
    public Boolean deleteTemplate(Long couponId) {
        // BaseServiceImpl.removeById 已填充 delete_at/delete_by 并执行逻辑删除
        boolean ok = removeById(couponId);
        log.info("删除优惠券模板 id={} success={}", couponId, ok);
        return ok;
    }

    @Override
    public CouponTemplateResp enableCoupon(Long couponId) {
        // 使用部分实体 + updateById,仅更新 status,避免覆盖其他字段
        CouponTemplate entity = new CouponTemplate();
        entity.setId(couponId);
        entity.setStatus(PromotionStatus.ACTIVE);
        int rows = baseMapper.updateById(entity);
        if (rows <= 0) {
            throw new ParamException("优惠券模板不存在");
        }
        log.info("启用优惠券模板 id={} status=ACTIVE", couponId);
        return getTemplate(couponId);
    }

    @Override
    public CouponTemplateResp disableCoupon(Long couponId) {
        // 停用 = 状态置为 EXPIRED(非 ACTIVE 即视为停用,issue/receive 均会拒绝发放/领取)
        CouponTemplate entity = new CouponTemplate();
        entity.setId(couponId);
        entity.setStatus(PromotionStatus.EXPIRED);
        int rows = baseMapper.updateById(entity);
        if (rows <= 0) {
            throw new ParamException("优惠券模板不存在");
        }
        log.info("停用优惠券模板 id={} status=EXPIRED", couponId);
        return getTemplate(couponId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponIssueResp issue(CouponIssueReq req) {
        CouponIssueResp resp = new CouponIssueResp();
        if (req.getMemberIds() == null || req.getMemberIds().isEmpty()) {
            resp.setSuccess(false);
            resp.setMessage("待发放会员列表为空");
            resp.setIssuedCount(0);
            resp.setFailedCount(0);
            return resp;
        }
        // 铁律 12:批量操作单批上限 50,避免长事务锁表与连接耗尽
        if (req.getMemberIds().size() > 50) {
            throw new ParamException("单批发放会员上限50，请分批发");
        }

        // 加载并校验模板
        CouponTemplate template = getById(req.getCouponId());
        if (template == null) {
            resp.setSuccess(false);
            resp.setMessage("优惠券模板不存在");
            resp.setIssuedCount(0);
            resp.setFailedCount(req.getMemberIds().size());
            return resp;
        }
        if (!PromotionStatus.ACTIVE.equals(template.getStatus())) {
            resp.setSuccess(false);
            resp.setMessage("优惠券模板已停用，不可发放");
            resp.setIssuedCount(0);
            resp.setFailedCount(req.getMemberIds().size());
            return resp;
        }

        int success = 0;
        int failed = 0;
        StringBuilder failMsg = new StringBuilder();
        for (Long memberId : req.getMemberIds()) {
            try {
                receiveCoupon(template, memberId);
                success++;
            } catch (BizException e) {
                failed++;
                failMsg.append("会员").append(memberId).append(":").append(e.getMsg()).append(";");
            }
        }

        // 同事务内累加模板 issued_count(SQL 级原子累加,避免内存值竞态)
        if (success > 0) {
            baseMapper.update(null, new UpdateWrapper<CouponTemplate>()
                    .eq("id", template.getId())
                    .setSql("issued_count = issued_count + " + success));
        }

        resp.setIssuedCount(success);
        resp.setFailedCount(failed);
        resp.setSuccess(failed == 0);
        resp.setMessage(failed == 0 ? "批量发放成功" : "部分发放失败：" + failMsg);
        log.info("批量发放优惠券 couponId={} requested={} success={} failed={}",
                req.getCouponId(), req.getMemberIds().size(), success, failed);
        if (failed > 0) {
            log.warn("批量发放优惠券部分失败 couponId={} failDetail={}", req.getCouponId(), failMsg.toString());
        }
        return resp;
    }

    /**
     * 私有发券方法:校验单会员可发放性 + 创建 user_coupon 记录.
     * <p>不直接更新 issued_count(由 issue 方法在循环结束后批量累加);
     * 但需在内存中累计 template.issuedCount,以便后续循环看到正确余额.
     * 抛出 BizException 表示该会员发放失败(不中断整体事务).
     */
    private void receiveCoupon(CouponTemplate template, Long memberId) {
        // 校验发放总量(totalCount=0 不限)
        if (template.getTotalCount() != null && template.getTotalCount() != 0
                && template.getIssuedCount() != null
                && template.getIssuedCount() >= template.getTotalCount()) {
            throw new ParamException("优惠券已发放完毕");
        }
        // 校验每人限领(统计该会员历史领取记录,已退的不计为可用但仍计为已领取事件)
        int perLimit = template.getPerLimit() == null ? 1 : template.getPerLimit();
        Long userCount = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getCouponId, template.getId())
                .eq(UserCoupon::getMemberId, memberId));
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
        uc.setMemberId(memberId);
        uc.setStatus(CouponStatus.UNUSED);
        uc.setFaceValue(template.getFaceValue());
        uc.setThreshold(template.getThreshold());
        uc.setReceiveTime(now);
        uc.setExpireTime(expireTime);
        // storeId / tenantId 不主动赋值,由门店/多租户拦截器自动注入
        userCouponMapper.insert(uc);

        // 内存中累计 issuedCount,使后续循环的 totalCount 校验生效
        if (template.getIssuedCount() == null) {
            template.setIssuedCount(0);
        }
        template.setIssuedCount(template.getIssuedCount() + 1);
    }

    // ========== 过期处理预留接口 ==========
    // 本期不实现定时任务扫描 expired 状态:可后续通过 Spring Scheduling 或 Quartz 调度
    // 调用 UserCouponMapper 批量 UPDATE WHERE status=unused AND expire_time<NOW() SET status=expired
}
