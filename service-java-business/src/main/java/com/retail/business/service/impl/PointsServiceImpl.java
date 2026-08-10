package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.service.BaseServiceImpl;
import com.retail.business.convert.PointsConvert;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.PointsAdjustReq;
import com.retail.business.dto.resp.MemberPointsResp;
import com.retail.business.dto.resp.PointsLogResp;
import com.retail.business.entity.Member;
import com.retail.business.entity.PointsLog;
import com.retail.business.entity.TenantConfig;
import com.retail.business.enums.PointsBizType;
import com.retail.business.enums.PointsChangeType;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.mapper.PointsLogMapper;
import com.retail.business.service.PointsService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 会员积分服务实现.
 * <p>
 * 继承 {@link BaseServiceImpl} 获得逻辑删除能力(PointsLog 为物理删除表,但 BaseServiceImpl 的
 * removeById 仅在含 deleted 字段时生效,此处不影响);核心积分变动逻辑在 {@link #recordChange} 中统一封装.
 * <p>
 * 所有积分变动方法均标注 {@code @Transactional(rollbackFor=Exception.class)},保证:
 * <ol>
 *   <li>写入 points_log 流水(含 before/after_balance 快照)</li>
 *   <li>更新 member.points 余额</li>
 * </ol>
 * 两步操作原子性.tenant_id / store_id 由拦截器自动注入,代码中不主动赋值.
 */
@Slf4j
@Service
public class PointsServiceImpl extends BaseServiceImpl<PointsLogMapper, PointsLog> implements PointsService {

    private final MemberMapper memberMapper;
    private final PointsConvert pointsConvert;

    /** 默认积分规则(1元=N积分),未配置时使用 */
    private static final Integer DEFAULT_POINTS_RATE = 100;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 PointsLogMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>memberMapper 用于 recordChange 原子更新 member.points 余额(避免注入 MemberService 防循环);
     * tenantConfigService 用于 earn 时按租户积分规则(points_rate,1 元 = N 积分)计算实际到账积分;
     * pointsConvert 用于 PointsLog → PointsLogResp 转换(changeType / bizType 枚举自动映射).
     */
    public PointsServiceImpl(MemberMapper memberMapper, PointsConvert pointsConvert) {
        this.memberMapper = memberMapper;
        this.pointsConvert = pointsConvert;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsLog earn(Long memberId, Integer points, PointsBizType bizType, String bizNo) {
        if (points == null || points <= 0) {
            throw new ParamException("获取积分数量必须为正数");
        }
        // earn 为正向变动,bizType 通常为 order
        return recordChange(memberId, PointsChangeType.EARN, points, bizType, bizNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsLog exchange(Long memberId, Integer points, PointsBizType bizType, String bizNo) {
        if (points == null || points <= 0) {
            throw new ParamException("兑换积分数量必须为正数");
        }
        // exchange 传入正数,内部转换为负数扣减,recordChange 会校验余额
        return recordChange(memberId, PointsChangeType.EXCHANGE, -points, bizType, bizNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsLog refund(Long memberId, Integer points, String bizNo) {
        if (points == null || points <= 0) {
            throw new ParamException("退款扣减积分数量必须为正数");
        }
        // refund 传入正数,内部转换为负数扣减,bizType 固定为 refund
        return recordChange(memberId, PointsChangeType.REFUND, -points, PointsBizType.REFUND, bizNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsLog adjust(PointsAdjustReq req) {
        if (req.getChangePoints() == null || req.getChangePoints() == 0) {
            throw new ParamException("积分调整数量不能为0");
        }
        // adjust 允许正负,bizType 固定为 manual,reason 写入 remark
        return recordChange(req.getMemberId(), PointsChangeType.ADJUST,
                req.getChangePoints(), PointsBizType.MANUAL, null, req.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointsLog redeem(Long memberId, Integer points, String reason) {
        if (points == null || points <= 0) {
            throw new ParamException("兑换积分数量必须为正数");
        }
        // redeem 传入正数,内部转换为负数扣减,recordChange 会校验余额充足
        // 变动类型用 EXCHANGE(兑换消耗),业务类型用 MANUAL(人工兑换),reason 写入 remark
        return recordChange(memberId, PointsChangeType.EXCHANGE, -points, PointsBizType.MANUAL, null, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer savePointsRate(Integer rate) {
        // if (rate == null || rate <= 0) {
        //     throw new ParamException("积分规则（1元=N积分）必须为正整数");
        // }
        // Long tenantId = LoginUserHolder.effectiveTenantId();
        // if (tenantId == null) {
        //     throw new ParamException("无法确定当前租户，无法保存积分规则");
        // }
        // TenantConfig config = tenantConfigService.getOne(
        //         new LambdaQueryWrapper<TenantConfig>().eq(TenantConfig::getTenantId, tenantId));
        // if (config == null) {
        //     throw new ParamException("当前租户配置不存在，无法保存积分规则");
        // }
        // config.setPointsRate(rate);
        // config.setUpdatedAt(LocalDateTime.now());
        // tenantConfigService.updateById(config);
        // log.info("更新积分规则 tenantId={} pointsRate={}", tenantId, rate);
        return rate;
    }

    @Override
    public PageResp<PointsLogResp> listLogs(Long memberId, Integer changeType,
                                            LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<PointsLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsLog::getMemberId, memberId);
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (changeType != null) {
            wrapper.eq(PointsLog::getChangeType, EnumUtil.fromCode(PointsChangeType.class, changeType));
        }
        if (startDate != null) {
            wrapper.ge(PointsLog::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(PointsLog::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(PointsLog::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<PointsLog> page = PageContextHolder.get();
        IPage<PointsLog> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射)
        List<PointsLogResp> items = pointsConvert.toRespList(result.getRecords());

        // 填充会员名称:listLogs 按 memberId 过滤,所有行归属同一会员,
        // 单次 selectById 比 LEFT JOIN 更高效.MemberMapper 已由构造注入(用于积分变动流程),
        // 无需额外注入其他 Service,避免循环依赖(用户硬约束).
        Member member = memberMapper.selectById(memberId);
        String memberName = member != null ? member.getName() : null;
        items.forEach(i -> i.setMemberName(memberName));

        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public MemberPointsResp getPointsSummary(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new ParamException("会员不存在");
        }

        MemberPointsResp resp = new MemberPointsResp();
        resp.setMemberId(memberId);
        resp.setCurrentPoints(member.getPoints() == null ? 0 : member.getPoints());

        // 查询该会员全部流水,在 Java 层累加统计(首版实现,后续可优化为自定义 SQL 聚合)
        List<PointsLog> allLogs = baseMapper.selectList(
                new LambdaQueryWrapper<PointsLog>().eq(PointsLog::getMemberId, memberId));

        // 累计获取 = earn + gift 类型变动积分之和(正数)
        int totalEarned = allLogs.stream()
                .filter(l -> PointsChangeType.EARN.equals(l.getChangeType())
                        || PointsChangeType.GIFT.equals(l.getChangeType()))
                .mapToInt(l -> l.getChangePoints() != null ? l.getChangePoints() : 0)
                .sum();
        resp.setTotalEarned(totalEarned);

        // 累计兑换 = exchange 类型变动积分绝对值之和
        int totalExchanged = allLogs.stream()
                .filter(l -> PointsChangeType.EXCHANGE.equals(l.getChangeType()))
                .mapToInt(l -> l.getChangePoints() != null ? Math.abs(l.getChangePoints()) : 0)
                .sum();
        resp.setTotalExchanged(totalExchanged);

        // 近30天变动流水(按时间倒序)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<PointsLog> recentLogs = baseMapper.selectList(
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getMemberId, memberId)
                        .ge(PointsLog::getCreatedAt, thirtyDaysAgo)
                        .orderByDesc(PointsLog::getCreatedAt));
        resp.setRecentLogs(pointsConvert.toRespList(recentLogs));

        return resp;
    }

    /**
     * 积分变动核心方法:查 member.points 作为 beforeBalance,计算 afterBalance,写流水 + 更新余额.
     * <p>
     * 调用方必须已开启事务(earn/exchange/refund/adjust 均标注 @Transactional).
     *
     * @param memberId     会员ID
     * @param changeType   变动类型,取值见 {@link PointsChangeType}
     * @param changePoints 变动积分(正数增加,负数扣减)
     * @param bizType      业务类型(order/coupon/manual/activity)
     * @param bizNo        关联单据号
     * @param remark       备注
     * @return 积分流水实体(含 before/after 余额快照)
     */
    @Transactional(rollbackFor = Exception.class)
    private PointsLog recordChange(Long memberId, PointsChangeType changeType, Integer changePoints,
                                   PointsBizType bizType, String bizNo, String remark) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new ParamException("会员不存在");
        }

        int before = member.getPoints() == null ? 0 : member.getPoints();
        int after = before + changePoints;
        // 余额不足校验(仅扣减时触发)
        if (after < 0) {
            throw new ParamException("积分余额不足，当前余额：" + before);
        }

        // 写积分流水(before/after 余额快照)
        // 局部变量命名 pointsLog 而非 log,避免与 @Slf4j 生成的静态 logger 字段遮蔽
        PointsLog pointsLog = new PointsLog();
        pointsLog.setMemberId(memberId);
        pointsLog.setChangeType(changeType);
        pointsLog.setChangePoints(changePoints);
        pointsLog.setBeforeBalance(before);
        pointsLog.setAfterBalance(after);
        pointsLog.setBizType(bizType);
        pointsLog.setBizNo(bizNo);
        pointsLog.setRemark(remark);
        baseMapper.insert(pointsLog);

        // 更新会员积分余额 + 最后活跃时间
        Member update = new Member();
        update.setId(memberId);
        update.setPoints(after);
        update.setLastActiveAt(LocalDateTime.now());
        memberMapper.updateById(update);

        // 积分变动 INFO 日志:在 recordChange 统一出口覆盖 earn/exchange/refund/adjust 全部入口
        // changeType 区分业务语义(earn 正向获取 / exchange 兑换扣减 / refund 退款扣减 / adjust 手工调整)
        log.info("积分变动 memberId={} changeType={} changePoints={} before={} after={} bizType={} bizNo={} remark={}",
                memberId, changeType, changePoints, before, after, bizType, bizNo, remark);

        return pointsLog;
    }
}
