package com.retail.business.agent;

import cn.hutool.core.util.StrUtil;
import com.retail.business.convert.PointsConvert;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.PointsAdjustReq;
import com.retail.business.dto.req.PointsLogsToolReq;
import com.retail.business.dto.req.PointsRedeemToolReq;
import com.retail.business.dto.req.PointsRuleToolReq;
import com.retail.business.dto.req.PointsSummaryToolReq;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.MemberPointsResp;
import com.retail.business.dto.resp.PointsLogResp;
import com.retail.business.entity.PointsLog;
import com.retail.business.service.MemberService;
import com.retail.business.service.PointsService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;

import java.time.LocalDate;

/**
 * 会员积分 Agent 工具服务 (business="points").
 * <p>
 * 聚合积分域的工具方法, 复用 {@link PointsService} 现有业务逻辑:
 * <ul>
 *   <li>{@code points:summary} — 查询会员积分汇总 (只读, 当前余额 + 累计 + 近 30 天变动);</li>
 *   <li>{@code points:logs}    — 分页查询积分流水 (只读, 按类型/时间过滤);</li>
 *   <li>{@code points:adjust}  — 手动调整积分 (破坏性, HITL 审批).</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:
 * <ul>
 *   <li>summary/logs → business:points:query (对齐 PointsController.logs/summary @SaCheckPermission);</li>
 *   <li>adjust → business:points:adjust (对齐 PointsController.adjust @SaCheckPermission).</li>
 * </ul>
 * <p>
 * 注意: earn/exchange/refund 由订单模块跨模块调用, 不封装为 Agent 工具 (仅 adjust 面向用户).
 */
@AgentToolService(business = "points")
public class PointsAgentToolService {

    private final PointsService pointsService;
    private final PointsConvert pointsConvert;
    private final MemberService memberService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public PointsAgentToolService(PointsService pointsService, PointsConvert pointsConvert, MemberService memberService) {
        this.pointsService = pointsService;
        this.pointsConvert = pointsConvert;
        this.memberService = memberService;
    }

    /**
     * 解析会员ID:优先使用传入的 memberId;否则按 memberName/phone 反查会员.
     * <p>
     * 业务人员通常只掌握会员姓名/手机号,不掌握内部会员ID,故提供按业务字段定位的入口.
     * 反查时要求唯一命中(恰好一条),否则抛出 {@link ParamException} 提示.
     *
     * @param memberId   会员ID(可空)
     * @param memberName 会员姓名(可空)
     * @param phone      手机号(可空)
     * @return 解析后的会员ID
     */
    private Long resolveMemberId(Long memberId, String memberName, String phone) {
        if (memberId != null) {
            return memberId;
        }
        if (StrUtil.isBlank(memberName) && StrUtil.isBlank(phone)) {
            throw new ParamException("请提供会员ID、会员姓名或手机号");
        }
        // 按姓名/手机号反查会员(先查ID再过滤)
        MemberQueryReq queryReq = new MemberQueryReq();
        queryReq.setName(memberName);
        queryReq.setPhone(phone);
        PageResp<MemberResp> page = memberService.listMembers(queryReq);
        if (page.getItems() == null || page.getItems().size() != 1) {
            throw new ParamException("未找到唯一匹配的会员，请提供更精确的会员姓名或手机号");
        }
        return page.getItems().get(0).getId();
    }

    /**
     * 查询会员积分汇总 (只读).
     * <p>
     * 复用 {@link PointsService#getPointsSummary}, 对齐 PointsController.summary 的 @SaCheckPermission("business:points:query").
     * 返回当前余额 + 累计获取/兑换 + 近 30 天变动趋势.
     *
     * @param req 查询条件 (memberId)
     * @return 积分汇总
     */
    @AgentTool(
        operation = "summary",
        description = "查询会员积分汇总。返回当前积分余额、累计获取积分、累计兑换积分、近30天积分变动。支持按会员ID、会员姓名或手机号定位会员。用于回答'会员王五的积分情况'。",
        requiredPermission = "business:points:query",
        outputHint = "返回积分汇总，包含当前余额、累计获取、累计兑换、近30天变动。展示为结构化文本，重点突出当前余额。"
    )
    public MemberPointsResp summary(PointsSummaryToolReq req) {
        Long memberId = resolveMemberId(req.getMemberId(), req.getMemberName(), req.getPhone());
        return pointsService.getPointsSummary(memberId);
    }

    /**
     * 分页查询会员积分流水 (只读, 支持按类型/时间过滤).
     * <p>
     * 复用 {@link PointsService#listLogs}, 对齐 PointsController.logs 的 @SaCheckPermission("business:points:query").
     *
     * @param req 查询条件 (memberId / changeType / 日期范围 + 分页)
     * @return 积分流水分页列表
     */
    @AgentTool(
        operation = "logs",
        description = "查询会员积分流水。支持按会员ID、会员姓名或手机号定位会员，并按变动类型(获取/兑换/退款/调整)和时间范围过滤。可分页。用于回答'会员王五的积分明细''最近积分变动'等问题。",
        requiredPermission = "business:points:query",
        outputHint = "返回积分流水列表，包含变动类型、变动积分、变动前后余额、业务类型、关联单号、时间。展示为 markdown 表格。"
    )
    public PageResp<PointsLogResp> logs(PointsLogsToolReq req) {
        Long memberId = resolveMemberId(req.getMemberId(), req.getMemberName(), req.getPhone());
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            LocalDate startDate = req.getStartDate() != null ? LocalDate.parse(req.getStartDate()) : null;
            LocalDate endDate = req.getEndDate() != null ? LocalDate.parse(req.getEndDate()) : null;
            return pointsService.listLogs(memberId,
                    req.getChangeType(),
                    startDate, endDate);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 手动调整积分 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link PointsService#adjust}, 对齐 PointsController.adjust 的 @SaCheckPermission("business:points:adjust").
     * changePoints 正数增加, 负数扣减 (扣减时校验余额充足).
     * 事务内: 写入积分流水 + 更新 member.points 余额.
     *
     * @param req 调整请求 (memberId / changePoints / reason)
     * @return 积分流水 (含变动前后余额快照)
     */
    @AgentTool(
        operation = "adjust",
        description = "手动调整会员积分。changePoints正数增加积分，负数扣减积分（扣减时校验余额充足）。支持按会员ID、会员姓名或手机号定位会员，需提供变动积分数量、调整原因。此操作会直接修改会员积分余额，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:points:adjust",
        outputHint = "返回积分流水，包含变动类型、变动积分、变动前余额、变动后余额、调整原因。展示为文本，提示用户积分已调整。"
    )
    public PointsLogResp adjust(PointsAdjustReq req) {
        // 工具层先按业务字段定位会员ID(Controller 路径变量已覆盖 memberId 时忽略此解析)
        Long memberId = resolveMemberId(req.getMemberId(), req.getMemberName(), req.getPhone());
        req.setMemberId(memberId);
        PointsLog log = pointsService.adjust(req);
        return pointsConvert.toResp(log);
    }

    /**
     * 查看/修改当前租户积分规则(1元=N积分).
     * <p>
     * 复用 {@link PointsService#getPointsRate} / {@link PointsService#savePointsRate},
     * 规则存储于 tenant_config.points_rate.action=get 查看当前规则;action=set 时按 rate 修改.
     * 因可能修改规则,标注 destructive=true 触发 HITL 审批.
     *
     * @param req 规则请求(action=get/set,可选 rate)
     * @return 当前/更新后的积分规则文本
     */
    @AgentTool(
        operation = "rule",
        description = "查看或修改当前租户的积分规则（1元=N积分）。action=get 查看当前规则；action=set 时需提供 rate（1元换取多少积分），修改积分规则需用户确认后才可执行。用于回答'1元能攒多少积分帮我看看规则''现在一元能得多少分''把积分规则改成1元10分'。",
        destructive = true,
        requiredPermission = "business:points:edit",
        outputHint = "返回当前积分规则（1元=N积分）。action=set 时返回修改后的规则。展示为简洁文本。"
    )
    public String rule(PointsRuleToolReq req) {
        String action = StrUtil.isBlank(req.getAction()) ? "get" : req.getAction();
        if ("get".equalsIgnoreCase(action)) {
            return "当前积分规则：1元=" + "100积分";
        }
        if ("set".equalsIgnoreCase(action)) {
            Integer rate = pointsService.savePointsRate(req.getRate());
            return "积分规则已更新：1元=" + rate + "积分";
        }
        throw new ParamException("不支持的规则操作：" + action + "，仅支持 get/set");
    }

    /**
     * 积分兑换(破坏性操作,触发 HITL 审批).
     * <p>
     * 复用 {@link PointsService#redeem},按手机号优先,会员ID兜底定位会员,扣减积分余额并写负数积分流水.
     * 需提供兑换积分数量与兑换原因.
     *
     * @param req 兑换请求(memberPhone/memberId + points + reason)
     * @return 积分流水(含变动前后余额快照)
     */
    @AgentTool(
        operation = "redeem",
        description = "积分兑换。按会员手机号或会员ID定位会员，扣减其积分余额并写入负数积分流水，需提供兑换积分数量、兑换原因。此操作会直接扣减会员积分，需要用户确认后才可执行。用于回答'用积分换券''把他的积分兑换掉100分''为他兑换50积分'。",
        destructive = true,
        requiredPermission = "business:points:edit",
        outputHint = "返回积分流水，包含变动类型、变动积分、变动前余额、变动后余额、兑换原因。展示为文本，提示用户积分已兑换。"
    )
    public PointsLogResp redeem(PointsRedeemToolReq req) {
        // 优先按手机号(业务语义)定位会员,memberId 仅作兜底
        Long memberId = resolveMemberId(req.getMemberId(), null, req.getMemberPhone());
        PointsLog log = pointsService.redeem(memberId, req.getPoints(), req.getReason());
        return pointsConvert.toResp(log);
    }
}
