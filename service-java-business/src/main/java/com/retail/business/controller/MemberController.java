package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.MemberCreateReq;
import com.retail.business.dto.req.MemberLevelAdjustReq;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.MemberUpdateReq;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.service.MemberService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员管理接口.
 * <p>路由前缀 /api/v1/members.member 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:member:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>列表 / 沉睡会员分页参数由 PageParameterInterceptor 自动注入 ThreadLocal,Req 不承载 page 字段.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 创建会员.
     * <p>手机号在租户内唯一校验,等级缺省为 NORMAL,积分初始 0.
     */
    @PostMapping
    @SaCheckPermission("business:member:add")
    public R<MemberResp> create(@RequestBody MemberCreateReq req) {
        return R.ok(memberService.createMember(req));
    }

    /**
     * 分页查询会员列表(多条件过滤:姓名 / 手机号 / 等级 / 积分区间 / 累计消费 / 订单数).
     */
    @GetMapping
    @SaCheckPermission("business:member:list")
    public R<PageResp<MemberResp>> list(MemberQueryReq req) {
        return R.ok(memberService.listMembers(req));
    }

    /**
     * 查询会员详情(含等级,积分,累计消费,最后活跃时间).
     */
    @GetMapping("/{memberId:\\d+}")
    @SaCheckPermission("business:member:query")
    public R<MemberResp> detail(@PathVariable Long memberId) {
        return R.ok(memberService.getMember(memberId));
    }

    /**
     * 更新会员资料(部分更新:姓名 / 手机号,变更手机号时做租户内唯一校验).
     */
    @PutMapping("/{memberId:\\d+}")
    @SaCheckPermission("business:member:edit")
    public R<MemberResp> update(@PathVariable Long memberId,
                                @RequestBody MemberUpdateReq req) {
        req.setMemberId(memberId);
        return R.ok(memberService.updateMember(req));
    }

    /**
     * 调整会员等级(NORMAL / SILVER / GOLD / DIAMOND).
     * <p>按 Integer code 反查枚举(EnumUtil.fromCode 校验非法值),等级变更写入日志.
     */
    @PostMapping("/{memberId:\\d+}/adjust-level")
    @SaCheckPermission("business:member:levelAdjust")
    public R<MemberResp> adjustLevel(@PathVariable Long memberId,
                                     @RequestBody MemberLevelAdjustReq req) {
        req.setMemberId(memberId);
        return R.ok(memberService.adjustLevel(req));
    }

    /**
     * 查询沉睡会员列表(last_active_at 距今超过 days 天未活跃的会员).
     */
    @GetMapping("/sleeping")
    @SaCheckPermission("business:member:sleeping")
    public R<PageResp<MemberResp>> sleeping(@RequestParam Integer days) {
        return R.ok(memberService.listSleeping(days));
    }
}
