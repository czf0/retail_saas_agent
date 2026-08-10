package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.retail.business.dto.req.MemberCreateReq;
import com.retail.business.dto.req.MemberDetailToolReq;
import com.retail.business.dto.req.MemberLevelAdjustReq;
import com.retail.business.dto.req.MemberOrdersToolReq;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.MemberQueryToolReq;
import com.retail.business.dto.req.MemberSleepingToolReq;
import com.retail.business.dto.req.MemberTagAssignReq;
import com.retail.business.dto.req.MemberUpdateReq;
import com.retail.business.dto.req.OrderQueryReq;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.OrderListItemResp;
import com.retail.business.service.MemberService;
import com.retail.business.service.MemberTagService;
import com.retail.business.service.OrderService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.exception.ParamException;


/**
 * 会员基本信息 Agent 工具服务 (business="member").
 * <p>
 * 聚合会员域的工具方法, 复用 {@link MemberService} 现有业务逻辑:
 * <ul>
 *   <li>{@code member:query}        — 分页查询会员列表 (只读, 按姓名/手机号/等级/积分/消费/订单数过滤);</li>
 *   <li>{@code member:detail}       — 查询会员详情 (只读, 按会员ID或手机号定位);</li>
 *   <li>{@code member:create}       — 新增会员 (破坏性, HITL 审批);</li>
 *   <li>{@code member:update}       — 更新会员资料 (破坏性, HITL 审批);</li>
 *   <li>{@code member:level_adjust} — 调整会员等级 (破坏性, HITL 审批);</li>
 *   <li>{@code member:orders}       — 查询会员历史订单 (只读);</li>
 *   <li>{@code member:sleeping}     — 沉睡会员识别 (只读).</li>
 * </ul>
 * <p>
 * 权限说明: 会员查询为租户内部数据, 依赖多租户隔离, requiredPermission 显式空串 (不自动推导, 无权限要求).
 */
@AgentToolService(business = "member")
public class MemberAgentToolService {

    private final MemberService memberService;
    private final MemberTagService memberTagService;
    private final OrderService orderService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public MemberAgentToolService(MemberService memberService,
                                  MemberTagService memberTagService,
                                  OrderService orderService) {
        this.memberService = memberService;
        this.memberTagService = memberTagService;
        this.orderService = orderService;
    }

    /**
     * 分页查询会员列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link MemberService#listMembers},业务人员以姓名/手机号/等级/积分/消费等语义定位,
     * 无需知道会员ID.
     *
     * @param req 查询条件 (name / phone / level / 积分区间 / 消费区间 / 订单数 + 分页)
     * @return 会员列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询会员列表。支持按姓名、手机号、会员等级、积分区间、累计消费区间、累计订单数过滤。可分页。用于回答'查会员王五''金卡会员有哪些''消费超1000的会员'等问题。",
        requiredPermission = "",
        outputHint = "返回会员列表，包含会员姓名、手机号、等级、积分、累计消费、累计订单数、最后下单时间。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<MemberResp> query(MemberQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // 同名字段复制到业务层 MemberQueryReq(分页参数不进入业务 Req)
            MemberQueryReq queryReq = new MemberQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return memberService.listMembers(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询会员详情 (只读).
     * <p>
     * 复用 {@link MemberService#getMember},支持按会员ID或手机号定位.
     *
     * @param req 查询条件 (memberId 或 phone)
     * @return 会员详情
     */
    @AgentTool(
        operation = "detail",
        description = "查询会员详情。返回会员完整信息，包括姓名、手机号、等级、积分、累计消费、累计订单数、最后下单/活跃时间。可通过会员ID或手机号定位。用于回答'会员王五的详细信息'。",
        requiredPermission = "",
        outputHint = "返回会员详情，包含姓名、手机号、等级、积分、累计消费、累计订单数、最后下单时间。展示为结构化文本，金额保留 2 位小数。"
    )
    public MemberResp detail(MemberDetailToolReq req) {
        if (req.getMemberId() != null) {
            return memberService.getMember(req.getMemberId());
        }
        if (StrUtil.isNotBlank(req.getPhone())) {
            // 按手机号反查会员ID(精确匹配)
            MemberQueryReq queryReq = new MemberQueryReq();
            queryReq.setPhone(req.getPhone());
            PageResp<MemberResp> page = memberService.listMembers(queryReq);
            if (page.getItems() == null || page.getItems().isEmpty()) {
                throw new ParamException("未找到该手机号对应的会员");
            }
            return page.getItems().get(0);
        }
        throw new ParamException("请提供会员ID或手机号");
    }

    /**
     * 解析会员ID:优先使用传入的 memberId;否则按 memberName/phone 反查会员(唯一命中).
     * <p>
     * 业务人员通常只掌握会员姓名/手机号,不掌握内部会员ID,故提供按业务字段定位的入口.
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
     * 新增会员 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link MemberService#createMember} 完成建档(手机号唯一校验),
     * 若入参携带 tagIds 则复用 {@link MemberTagService#assignTags} 分配标签.
     *
     * @param req 创建请求 (name/phone/level/points/tagIds)
     * @return 新增后的会员详情
     */
    @AgentTool(
        operation = "create",
        description = "新增会员（办卡建档）。需要会员姓名，可选填手机号、会员等级（会员等级为整数code：1普通/2银卡/3金卡/4钻石，必须传数字，如金卡传3）、初始积分、标签ID列表。手机号租户内唯一，重复会被拒绝。用于回答'给张三办一张会员卡''新增会员张三 13812345678''新来顾客录一下，办张金卡'等问题。此操作会新增会员记录，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:member:add",
        outputHint = "返回新增的会员信息，包含会员ID、姓名、手机号、等级、积分。展示为结构化文本，提示用户会员已建档成功。"
    )
    public MemberResp create(MemberCreateReq req) {
        MemberResp member = memberService.createMember(req);
        // 可选打标签(复用 membertag 服务,自动去重)
        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            MemberTagAssignReq tagAssign = new MemberTagAssignReq();
            tagAssign.setMemberId(member.getId());
            tagAssign.setTagIds(req.getTagIds());
            memberTagService.assignTags(tagAssign);
        }
        return member;
    }

    /**
     * 更新会员资料 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link MemberService#updateMember},支持按会员ID/姓名/手机号定位,
     * 修改姓名/手机号(null 不更新,手机号变更校验唯一).
     *
     * @param req 更新请求 (定位 + newName/newPhone)
     * @return 更新后的会员详情
     */
    @AgentTool(
        operation = "update",
        description = "更新会员资料。支持修改会员姓名、手机号。支持按会员ID、会员姓名或手机号定位会员。未提供的字段保持不变。用于回答'把会员王五的手机号改成13900000000''把张三的名字改成李四'等问题。此操作会修改会员资料，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:member:edit",
        outputHint = "返回更新后的会员信息，包含会员ID、姓名、手机号、等级。展示为结构化文本，提示用户会员资料已更新。"
    )
    public MemberResp update(MemberUpdateReq req) {
        return memberService.updateMember(req);
    }

    /**
     * 调整会员等级 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link MemberService#adjustLevel},支持按会员ID/姓名/手机号定位,
     * 直接更新 member.level(不建独立历史表).
     *
     * @param req 调整请求 (定位 + newLevel + reason)
     * @return 调整后的会员详情
     */
    @AgentTool(
        operation = "level_adjust",
        description = "调整会员等级。支持将会员升级或降级为普通(1)/银卡(2)/金卡(3)/钻石(4)。目标等级必须传整数code，如升金卡传newLevel=3。支持按会员ID、会员姓名或手机号定位会员。用于回答'把李四升到金卡''给王五降为普通会员''修改会员张三的等级为钻石'等问题。此操作会修改会员等级，需要用户确认后才可执行。",
        destructive = true,
        requiredPermission = "business:member:edit",
        outputHint = "返回调整后的会员信息，包含会员ID、姓名、等级。展示为结构化文本，提示用户会员等级已调整。"
    )
    public MemberResp levelAdjust(MemberLevelAdjustReq req) {
        return memberService.adjustLevel(req);
    }

    /**
     * 查询会员历史订单 (只读).
     * <p>
     * 复用 {@link OrderService#listOrders}(按 memberId 过滤),支持按会员ID/姓名/手机号定位.
     *
     * @param req 查询条件 (定位 + 分页)
     * @return 会员历史订单分页列表
     */
    @AgentTool(
        operation = "orders",
        description = "查询会员的历史订单。支持按会员ID、会员姓名或手机号定位会员，返回该会员的历史订单列表（订单号、金额、状态、下单时间）。可分页。用于回答'会员王五最近买过什么''查一下张三的订单记录'等问题。",
        requiredPermission = "business:member:query",
        outputHint = "返回会员历史订单列表，包含订单号、金额、状态、下单时间。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<OrderListItemResp> orders(MemberOrdersToolReq req) {
        Long memberId = resolveMemberId(req.getMemberId(), req.getMemberName(), req.getPhone());
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            OrderQueryReq queryReq = new OrderQueryReq();
            queryReq.setMemberId(memberId);
            return orderService.listOrders(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 沉睡会员识别 (只读).
     * <p>
     * 复用 {@link MemberService#listSleeping},返回 last_active_at 距今超过 days 天的会员.
     *
     * @param req 查询条件 (days + 分页)
     * @return 沉睡会员分页列表
     */
    @AgentTool(
        operation = "sleeping",
        description = "识别沉睡会员。返回超过指定天数（如90天）未活跃/未消费的会员列表。可分页。用于回答'有哪些超90天没消费的会员''沉睡会员名单''多少天没来的会员'等问题。",
        requiredPermission = "business:member:query",
        outputHint = "返回沉睡会员列表，包含会员姓名、手机号、等级、最后活跃时间。展示为 markdown 表格，突出最后活跃时间以便判断沉睡程度。"
    )
    public PageResp<MemberResp> sleeping(MemberSleepingToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            return memberService.listSleeping(req.getDays());
        } finally {
            PageContextHolder.clear();
        }
    }
}
