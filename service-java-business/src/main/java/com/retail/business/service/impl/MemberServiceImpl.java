package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.StatsConvert;
import com.retail.business.dto.req.MemberCreateReq;
import com.retail.business.dto.req.MemberLevelAdjustReq;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.MemberUpdateReq;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.entity.Member;
import com.retail.business.enums.MemberLevel;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.service.MemberService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 会员查询服务实现.
 * <p>
 * 复用 {@link StatsConvert} 完成 Member→MemberResp 转换(level 枚举→Integer code 等映射已配置).
 * 分页参数由服务层从 {@code PageContextHolder} 读取,member 表为租户隔离表,tenant_id 由拦截器自动注入.
 */
@Slf4j
@Service
public class MemberServiceImpl extends BaseServiceImpl<MemberMapper, Member> implements MemberService {

    private final StatsConvert statsConvert;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 MemberMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>statsConvert 复用于 Member → MemberResp 转换(level 枚举 → Integer code 等映射已配置),
     * 避免新建 MemberConvert 造成 Converter 冗余.
     */
    public MemberServiceImpl(StatsConvert statsConvert) {
        this.statsConvert = statsConvert;
    }

    @Override
    public PageResp<MemberResp> listMembers(MemberQueryReq req) {
        if (req == null) {
            req = new MemberQueryReq();
        }
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(req.getName())) {
            wrapper.like(Member::getName, req.getName());
        }
        if (StrUtil.isNotBlank(req.getPhone())) {
            wrapper.like(Member::getPhone, req.getPhone());
        }
        if (req.getLevel() != null) {
            // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
            wrapper.eq(Member::getLevel, EnumUtil.fromCode(MemberLevel.class, req.getLevel()));
        }
        if (req.getMinPoints() != null) {
            wrapper.ge(Member::getPoints, req.getMinPoints());
        }
        if (req.getMaxPoints() != null) {
            wrapper.le(Member::getPoints, req.getMaxPoints());
        }
        if (req.getMinTotalSpent() != null) {
            wrapper.ge(Member::getTotalSpent, req.getMinTotalSpent());
        }
        if (req.getMaxTotalSpent() != null) {
            wrapper.le(Member::getTotalSpent, req.getMaxTotalSpent());
        }
        if (req.getMinTotalOrders() != null) {
            wrapper.ge(Member::getTotalOrders, req.getMinTotalOrders());
        }
        wrapper.orderByDesc(Member::getPoints);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal(Agent 工具路径手动注入)
        Page<Member> pageObj = PageContextHolder.get();
        IPage<Member> result = this.baseMapper.selectPage(pageObj, wrapper);
        return new PageResp<>(statsConvert.toMemberRespList(result.getRecords()),
                result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    @Override
    public MemberResp getMember(Long memberId) {
        Member member = this.getById(memberId);
        return member == null ? null : statsConvert.toMemberResp(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResp createMember(MemberCreateReq req) {
        if (req == null || StrUtil.isBlank(req.getName())) {
            throw new ParamException("会员姓名不能为空");
        }
        // 手机号唯一校验(租户内,tenant_id 由拦截器注入)
        if (StrUtil.isNotBlank(req.getPhone()) && existsByPhone(req.getPhone())) {
            throw new ParamException("该手机号已存在会员");
        }
        Member member = new Member();
        member.setName(req.getName().trim());
        member.setPhone(StrUtil.isBlank(req.getPhone()) ? null : req.getPhone().trim());
        // 等级:Integer code → 枚举(EnumUtil.fromCode 校验非法值);缺省普通
        member.setLevel(req.getLevel() == null
                ? MemberLevel.NORMAL
                : EnumUtil.fromCode(MemberLevel.class, req.getLevel()));
        // 初始积分缺省 0
        member.setPoints(req.getPoints() == null ? 0 : req.getPoints());
        // 初始累计消费/订单数/活跃时间
        member.setTotalSpent(java.math.BigDecimal.ZERO);
        member.setTotalOrders(0);
        member.setLastActiveAt(LocalDateTime.now());
        this.save(member);
        log.info("新增会员 memberId={} name={} phone={} level={}", member.getId(), member.getName(), member.getPhone(), member.getLevel());
        return statsConvert.toMemberResp(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResp updateMember(MemberUpdateReq req) {
        if (req == null) {
            throw new ParamException("更新请求不能为空");
        }
        Member member = resolveMember(req.getMemberId(), req.getMemberName(), req.getPhone());
        boolean changed = false;
        if (StrUtil.isNotBlank(req.getNewName())) {
            member.setName(req.getNewName().trim());
            changed = true;
        }
        if (req.getNewPhone() != null) {
            String newPhone = req.getNewPhone().trim();
            // 手机号变更唯一校验(排除当前会员自身)
            if (StrUtil.isNotBlank(newPhone) && existsByPhoneExclude(newPhone, member.getId())) {
                throw new ParamException("该手机号已存在会员");
            }
            member.setPhone(StrUtil.isBlank(newPhone) ? null : newPhone);
            changed = true;
        }
        if (changed) {
            this.updateById(member);
            log.info("更新会员 memberId={} newName={} newPhone={}", member.getId(), req.getNewName(), req.getNewPhone());
        }
        return statsConvert.toMemberResp(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResp adjustLevel(MemberLevelAdjustReq req) {
        if (req == null || req.getNewLevel() == null) {
            throw new ParamException("目标等级不能为空");
        }
        // Integer code → 枚举,非法值抛异常(EnumUtil.fromCode 校验)
        MemberLevel newLevel = EnumUtil.fromCode(MemberLevel.class, req.getNewLevel());
        Member member = resolveMember(req.getMemberId(), req.getMemberName(), req.getPhone());
        if (newLevel != member.getLevel()) {
            member.setLevel(newLevel);
            this.updateById(member);
            log.info("调整会员等级 memberId={} level={} reason={}", member.getId(), newLevel, req.getReason());
        }
        return statsConvert.toMemberResp(member);
    }

    @Override
    public PageResp<MemberResp> listSleeping(Integer days) {
        // 沉睡阈值:last_active_at 距今超过 days 天(含从未活跃的会员由 last_order_at/last_active_at NULL 兜底不出现在本查询)
        if (days == null || days < 1) {
            throw new ParamException("无活跃天数阈值必须 >= 1");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<Member>()
                .lt(Member::getLastActiveAt, cutoff)
                .orderByDesc(Member::getLastOrderAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal(Agent 工具路径手动注入)
        Page<Member> pageObj = PageContextHolder.get();
        IPage<Member> result = this.baseMapper.selectPage(pageObj, wrapper);
        return new PageResp<>(statsConvert.toMemberRespList(result.getRecords()),
                result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    // ===================== 私有辅助 =====================

    /**
     * 定位会员:优先 memberId,否则按 memberName/phone 精确匹配(要求唯一命中).
     */
    private Member resolveMember(Long memberId, String memberName, String phone) {
        if (memberId != null) {
            Member member = this.getById(memberId);
            if (member == null) {
                throw new ParamException("会员不存在");
            }
            return member;
        }
        if (StrUtil.isBlank(memberName) && StrUtil.isBlank(phone)) {
            throw new ParamException("请提供会员ID、会员姓名或手机号");
        }
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(memberName)) {
            wrapper.eq(Member::getName, memberName.trim());
        }
        if (StrUtil.isNotBlank(phone)) {
            wrapper.eq(Member::getPhone, phone.trim());
        }
        Member member = this.getOne(wrapper);
        if (member == null) {
            throw new ParamException("未找到匹配的会员，请核对姓名或手机号");
        }
        return member;
    }

    /** 手机号是否已存在(租户内,tenant_id 自动注入) */
    private boolean existsByPhone(String phone) {
        return this.count(new LambdaQueryWrapper<Member>().eq(Member::getPhone, phone.trim())) > 0;
    }

    /** 手机号是否已存在(排除指定会员自身,用于变更校验) */
    private boolean existsByPhoneExclude(String phone, Long excludeId) {
        return this.count(new LambdaQueryWrapper<Member>()
                .eq(Member::getPhone, phone.trim())
                .ne(Member::getId, excludeId)) > 0;
    }
}
