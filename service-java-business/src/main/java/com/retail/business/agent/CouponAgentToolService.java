package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.CouponDetailToolReq;
import com.retail.business.dto.req.CouponIssueReq;
import com.retail.business.dto.req.CouponQueryReq;
import com.retail.business.dto.req.CouponQueryToolReq;
import com.retail.business.dto.req.CouponRedeemRecordsToolReq;
import com.retail.business.dto.req.CouponRedeemStatsToolReq;
import com.retail.business.dto.req.CouponStatusToolReq;
import com.retail.business.dto.req.CouponTemplateCreateReq;
import com.retail.business.dto.req.CouponTemplateQueryReq;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.CouponIssueResp;
import com.retail.business.dto.resp.CouponTemplateListItemResp;
import com.retail.business.dto.resp.CouponTemplateResp;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.report.CouponRedeemResp;
import com.retail.business.entity.CouponTemplate;
import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.CouponType;
import com.retail.business.mapper.CouponTemplateMapper;
import com.retail.business.service.CouponReportService;
import com.retail.business.service.CouponService;
import com.retail.business.service.MemberService;
import com.retail.business.service.UserCouponService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 优惠券业务 Agent 工具服务 (business="coupon").
 * <p>
 * 聚合优惠券域的工具方法, 复用 {@link CouponService} 现有业务逻辑:
 * <ul>
 *   <li>{@code coupon:query}  — 分页查询优惠券模板 (只读, 多条件过滤);</li>
 *   <li>{@code coupon:detail} — 查询优惠券模板详情 (只读);</li>
 *   <li>{@code coupon:create} — 创建优惠券模板 (破坏性, HITL 审批);</li>
 *   <li>{@code coupon:issue}  — 批量发放优惠券 (破坏性, HITL 审批);</li>
 *   <li>{@code coupon:enable}  — 启用优惠券模板 (破坏性, HITL 审批);</li>
 *   <li>{@code coupon:disable} — 停用优惠券模板 (破坏性, HITL 审批);</li>
 *   <li>{@code coupon:redeem_records} — 核销记录明细查询 (只读);</li>
 *   <li>{@code coupon:redeem_stats}  — 核销统计 (只读).</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:
 * <ul>
 *   <li>query/detail → business:coupon:query (对齐 CouponController.list/detail @SaCheckPermission);</li>
 *   <li>create → business:coupon:add (对齐 CouponController.create @SaCheckPermission);</li>
 *   <li>issue → business:coupon:issue (对齐 CouponController.issue @SaCheckPermission);</li>
 *   <li>enable/disable → business:coupon:edit (对齐 CouponController.update @SaCheckPermission);</li>
 *   <li>redeem_records → business:coupon:query;</li>
 *   <li>redeem_stats → business:report:coupon (对齐 ReportController 营销报表权限).</li>
 * </ul>
 */
@AgentToolService(business = "coupon")
public class CouponAgentToolService {

    private final CouponService couponService;
    private final CouponTemplateMapper couponTemplateMapper;
    private final MemberService memberService;
    private final UserCouponService userCouponService;
    private final CouponReportService couponReportService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public CouponAgentToolService(CouponService couponService, CouponTemplateMapper couponTemplateMapper,
                                  MemberService memberService, UserCouponService userCouponService,
                                  CouponReportService couponReportService) {
        this.couponService = couponService;
        this.couponTemplateMapper = couponTemplateMapper;
        this.memberService = memberService;
        this.userCouponService = userCouponService;
        this.couponReportService = couponReportService;
    }

    /**
     * 解析优惠券模板ID:优先使用传入的 couponId;否则按券名反查模板.
     * <p>
     * 业务人员通常只掌握券名,不掌握内部模板ID,故提供按券名定位的入口.
     * 反查要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param couponId 优惠券模板ID(可空)
     * @param name     券名(可空)
     * @return 解析后的优惠券模板ID
     */
    private Long resolveCouponId(Long couponId, String name) {
        if (couponId != null) {
            return couponId;
        }
        if (StrUtil.isBlank(name)) {
            throw new ParamException("请提供优惠券模板ID或券名");
        }
        CouponTemplate template = couponTemplateMapper.selectOne(
                new LambdaQueryWrapper<CouponTemplate>().eq(CouponTemplate::getName, name));
        if (template == null) {
            throw new ParamException("未找到匹配的优惠券模板，请提供更精确的券名");
        }
        return template.getId();
    }

    /**
     * 解析发放会员ID集合:memberIds 非空时直接返回;否则按会员姓名/手机号/等级反查会员.
     * <p>
     * 业务人员通常只掌握会员姓名/手机号/等级,不掌握内部会员ID,故提供按业务字段定位的入口.
     * 反查无结果时抛出 {@link ParamException} 提示.
     *
     * @param memberIds    会员ID列表(可空)
     * @param memberName   会员姓名(可空)
     * @param memberPhone  会员手机号(可空)
     * @param memberLevel  会员等级(可空)
     * @return 解析后的会员ID集合
     */
    private List<Long> resolveMemberIds(List<Long> memberIds, String memberName, String memberPhone, Integer memberLevel) {
        if (CollUtil.isNotEmpty(memberIds)) {
            return memberIds;
        }
        if (StrUtil.isBlank(memberName) && StrUtil.isBlank(memberPhone) && memberLevel == null) {
            throw new ParamException("请提供会员ID列表、会员姓名、手机号或会员等级");
        }
        // 按会员姓名/手机号/等级反查会员(先查ID再过滤)
        MemberQueryReq queryReq = new MemberQueryReq();
        queryReq.setName(memberName);
        queryReq.setPhone(memberPhone);
        queryReq.setLevel(memberLevel);
        // 手动注入分页(默认取足够大的页大小,避免遗漏匹配会员)
        PageContextHolder.set(PageContextHolder.build(1, 10000));
        try {
            PageResp<MemberResp> page = memberService.listMembers(queryReq);
            if (page.getItems() == null || page.getItems().isEmpty()) {
                throw new ParamException("未找到匹配的会员，请提供更精确的会员姓名、手机号或等级");
            }
            return page.getItems().stream().map(MemberResp::getId).collect(Collectors.toList());
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 分页查询优惠券模板 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link CouponService#listTemplates}, 对齐 CouponController.list 的 @SaCheckPermission("business:coupon:query").
     *
     * @param req 查询条件 (status / type / keyword + 分页)
     * @return 优惠券模板列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询优惠券模板列表。支持按状态(启用/停用)、类型(满减/折扣/代金券)、名称关键词、面额区间、使用门槛区间、有效期范围过滤。可分页。用于回答'有哪些优惠券''满100减20的券''本月到期的券'等问题。",
        requiredPermission = "business:coupon:query",
        outputHint = "返回优惠券模板列表，包含名称、类型、面额、门槛、有效期、发放总量、已发放量、状态。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<CouponTemplateListItemResp> query(CouponQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // 同名字段复制到业务层 CouponTemplateQueryReq(分页参数不进入业务 Req)
            CouponTemplateQueryReq queryReq = new CouponTemplateQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return couponService.listTemplates(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询优惠券模板详情 (只读).
     * <p>
     * 复用 {@link CouponService#getTemplate}, 对齐 CouponController.detail 的 @SaCheckPermission("business:coupon:query").
     *
     * @param req 查询条件 (couponId / name)
     * @return 优惠券模板详情
     */
    @AgentTool(
        operation = "detail",
        description = "查询优惠券模板详情。支持按优惠券模板ID或券名定位，返回模板完整信息，包括类型、面额、使用门槛、有效期规则、发放总量、每人限领、关联促销等。用于回答'优惠券XX的详细信息'。",
        requiredPermission = "business:coupon:query",
        outputHint = "返回优惠券详情，包含名称、类型、面额、门槛、有效期类型、有效期、发放总量、已发放量、每人限领、状态。展示为结构化文本，金额保留 2 位小数。"
    )
    public CouponTemplateResp detail(CouponDetailToolReq req) {
        Long couponId = resolveCouponId(req.getCouponId(), req.getName());
        return couponService.getTemplate(couponId);
    }

    /**
     * 创建优惠券模板 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link CouponService#createTemplate}, 对齐 CouponController.create 的 @SaCheckPermission("business:coupon:add").
     * 支持满减(fullcut)/折扣(discount)/代金券(cash)三种类型, 相对/固定两种有效期.
     *
     * @param req 创建请求 (name / type / faceValue / threshold / validType / validDays / validStart / validEnd / totalCount / perLimit)
     * @return 优惠券模板详情 (含模板 ID)
     */
    @AgentTool(
        operation = "create",
        description = "创建优惠券模板。需要名称、类型(满减/折扣/代金券)、面额、使用门槛、有效期规则、发放总量、每人限领。满减券需指定满减金额和门槛，折扣券需指定折扣率(如0.8表示8折)。此操作会创建优惠券模板，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:coupon:add",
        outputHint = "返回创建结果，包含优惠券模板ID、名称、类型、面额、状态。展示为文本，提示用户优惠券模板已创建成功。"
    )
    public CouponTemplateResp create(CouponTemplateCreateReq req) {
        return couponService.createTemplate(req);
    }

    /**
     * 批量发放优惠券给指定会员 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link CouponService#issue}, 对齐 CouponController.issue 的 @SaCheckPermission("business:coupon:issue").
     * 事务保证: 模板 issued_count++ 与 user_coupon 创建同事务.
     * 对单个会员的失败不影响其他会员, 最终返回成功/失败计数.
     *
     * @param req 发放请求 (couponId / memberIds 或 memberName/memberPhone/memberLevel / storeId)
     * @return 发放结果 (成功数 / 失败数)
     */
    @AgentTool(
        operation = "issue",
        description = "批量发放优惠券给指定会员。需要优惠券模板ID，发放对象支持会员ID列表，或按会员姓名/手机号/等级定位会员。发放后模板已发放量增加，会员获得可用优惠券。对单个会员的失败不影响其他会员。此操作会向会员发放优惠券，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:coupon:issue",
        outputHint = "返回发放结果，包含成功数、失败数、失败会员列表。展示为文本，提示用户发放完成。"
    )
    public CouponIssueResp issue(CouponIssueReq req) {
        // 工具层先按会员姓名/手机号/等级定位会员ID(memberIds 为空时)
        req.setMemberIds(resolveMemberIds(req.getMemberIds(), req.getMemberName(), req.getMemberPhone(), req.getMemberLevel()));
        return couponService.issue(req);
    }

    /**
     * 启用优惠券模板 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link CouponService#enableCoupon}, 权限对齐 CouponController.update 的 @SaCheckPermission("business:coupon:edit").
     * 启用后模板状态置为 ACTIVE, 恢复可发放/领取.
     *
     * @param req 定位参数 (couponId / name)
     * @return 启用后的优惠券模板详情
     */
    @AgentTool(
        operation = "enable",
        description = "启用优惠券模板。支持按券名或券ID定位，启用后该券恢复可发放/领取。此操作会修改优惠券模板状态，需要用户确认后才可执行。用于回答'恢复这张券的发放''把满减20元券重新启用'等问题。",
        destructive = true,
        requiredPermission = "business:coupon:edit",
        outputHint = "返回启用的优惠券模板详情，包含名称、类型、面额、状态。展示为文本，提示用户优惠券模板已启用。"
    )
    public CouponTemplateResp enable(CouponStatusToolReq req) {
        Long couponId = resolveCouponId(req.getCouponId(), req.getName());
        return couponService.enableCoupon(couponId);
    }

    /**
     * 停用优惠券模板 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link CouponService#disableCoupon}, 权限对齐 CouponController.update 的 @SaCheckPermission("business:coupon:edit").
     * 停用后模板状态置为 EXPIRED, 不可再发放/领取, 但对已发出的券无影响.
     *
     * @param req 定位参数 (couponId / name)
     * @return 停用后的优惠券模板详情
     */
    @AgentTool(
        operation = "disable",
        description = "停用优惠券模板。支持按券名或券ID定位，停用后该券不可再发放/领取，但对已发出的券无影响。此操作会修改优惠券模板状态，需要用户确认后才可执行。用于回答'这张券先停发''别再发这张券了'等问题。",
        destructive = true,
        requiredPermission = "business:coupon:edit",
        outputHint = "返回停用的优惠券模板详情，包含名称、类型、面额、状态。展示为文本，提示用户优惠券模板已停用。"
    )
    public CouponTemplateResp disable(CouponStatusToolReq req) {
        Long couponId = resolveCouponId(req.getCouponId(), req.getName());
        return couponService.disableCoupon(couponId);
    }

    /**
     * 查询优惠券核销记录明细 (只读, 分页).
     * <p>
     * 复用 {@link UserCouponService#listUserCoupons} 的分页查询能力, 支持按券模板(ID/券名),会员,状态过滤,
     * 返回已发放券的领用与核销明细. 状态 code 对齐 CouponStatus 枚举 (1未使用/2已使用/3已过期/4已退).
     *
     * @param req 过滤条件 (couponId / name / memberId / status + 分页)
     * @return 核销记录分页列表
     */
    @AgentTool(
        operation = "redeem_records",
        description = "查询优惠券核销记录明细。支持按券名或券ID、会员、状态(未使用/已核销/已过期/已退)过滤，分页返回已发放券的领用与核销明细。用于回答'这张券发给了哪些人''看看上周那张券核销了多少''这个会员名下的券'等问题。",
        requiredPermission = "business:coupon:query",
        outputHint = "返回优惠券核销记录分页列表，包含券名、会员、状态、面额、领取时间、核销时间、核销订单号。展示为 markdown 表格，并提示总条数。"
    )
    public PageResp<UserCouponListItemResp> redeemRecords(CouponRedeemRecordsToolReq req) {
        // 券名优先定位模板ID:仅提供券名时反查;仅提供券ID或都不提供则保持原样(null=不按模板过滤)
        Long couponId = null;
        if (req.getCouponId() != null || StrUtil.isNotBlank(req.getName())) {
            couponId = resolveCouponId(req.getCouponId(), req.getName());
        }
        // 状态 code 校验非法值
        if (req.getStatus() != null) {
            EnumUtil.fromCode(CouponStatus.class, req.getStatus());
        }

        CouponQueryReq queryReq = new CouponQueryReq();
        queryReq.setCouponId(couponId);
        queryReq.setMemberId(req.getMemberId());
        queryReq.setStatus(req.getStatus());
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            return userCouponService.listUserCoupons(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询优惠券核销统计 (只读).
     * <p>
     * 复用 {@link CouponReportService#getRedeemRate}, 按时间范围统计各券模板的发放数,已使用数及核销率.
     * 支持按券名过滤; 类型仅做 code 合法性校验 (报表响应不含类型字段, 不参与过滤).
     *
     * @param req 统计条件 (name / type / startDate / endDate)
     * @return 各优惠券核销统计列表
     */
    @AgentTool(
        operation = "redeem_stats",
        description = "查询优惠券核销统计。按券模板统计发放数、已核销数及核销率，支持券名、券类型、时间范围过滤。用于回答'看看上周那张券核销了多少''这个月的券核销率怎么样''哪些券用得多'等问题。",
        requiredPermission = "business:report:coupon",
        outputHint = "返回优惠券核销统计列表，包含券名、发放数、已核销数、核销率(百分比)。展示为 markdown 表格，核销率保留 2 位小数。"
    )
    public List<CouponRedeemResp> redeemStats(CouponRedeemStatsToolReq req) {
        // 类型 code 校验非法值
        if (req.getType() != null) {
            EnumUtil.fromCode(CouponType.class, req.getType());
        }
        // 时间字符串 yyyy-MM-dd → 当天 00:00:00 / 23:59:59 的 LocalDateTime
        ReportTimeRangeReq timeRange = new ReportTimeRangeReq();
        try {
            if (StrUtil.isNotBlank(req.getStartDate())) {
                timeRange.setStartDate(LocalDate.parse(req.getStartDate()).atStartOfDay());
            }
            if (StrUtil.isNotBlank(req.getEndDate())) {
                timeRange.setEndDate(LocalDate.parse(req.getEndDate()).atTime(LocalTime.MAX));
            }
        } catch (Exception e) {
            throw new ParamException("日期格式应为 yyyy-MM-dd，请检查 startDate/endDate");
        }
        List<CouponRedeemResp> list = couponReportService.getRedeemRate(timeRange);
        // 按券名精确过滤(报表响应带 couponName)
        if (StrUtil.isNotBlank(req.getName())) {
            list = list.stream()
                    .filter(item -> item.getCouponName() != null && item.getCouponName().contains(req.getName()))
                    .collect(Collectors.toList());
        }
        return list;
    }
}
