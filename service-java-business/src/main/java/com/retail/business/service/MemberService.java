package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.MemberCreateReq;
import com.retail.business.dto.req.MemberLevelAdjustReq;
import com.retail.business.dto.req.MemberQueryReq;
import com.retail.business.dto.req.MemberUpdateReq;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.entity.Member;
import com.retail.core.dto.PageResp;

/**
 * 会员查询服务.
 * <p>
 * 提供会员基本信息的业务语义查询能力(按姓名/手机号/等级/积分/消费/订单数过滤),
 * 供 HTTP 接口与 {@code member:query} Agent 工具复用.member 表为租户隔离表(非门店隔离),
 * tenant_id 由拦截器自动注入.
 */
public interface MemberService extends IService<Member> {

    /**
     * 分页查询会员列表(多条件过滤).
     *
     * @param req 查询条件(含业务过滤字段)
     * @return 会员分页响应
     */
    PageResp<MemberResp> listMembers(MemberQueryReq req);

    /**
     * 查询会员详情.
     *
     * @param memberId 会员ID
     * @return 会员响应;不存在返回 null
     */
    MemberResp getMember(Long memberId);

    /**
     * 新增会员(建档).
     * <p>手机号租户内唯一校验;等级默认普通,积分默认 0.事务内写入 member 表.
     *
     * @param req 新增请求(name/phone/level/points)
     * @return 新增后的会员响应
     */
    MemberResp createMember(MemberCreateReq req);

    /**
     * 更新会员资料(部分更新:姓名/手机号,null 不更新).
     * <p>手机号变更时校验租户内唯一.
     *
     * @param req 更新请求(定位 + newName/newPhone)
     * @return 更新后的会员响应
     */
    MemberResp updateMember(MemberUpdateReq req);

    /**
     * 调整会员等级 (直接更新 member.level, 不建独立历史表).
     * <p>前置条件: 会员必须存在, 否则抛 BizException; 新等级必须是合法等级枚举, 否则抛 ParamException.
     * <p>副作用: 等级变更即时生效, 影响后续积分获取倍率与等级权益判断; 无异步事件.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param req 调整请求 (定位 + newLevel + reason)
     * @return 调整后的会员响应
     * @throws ParamException 新等级非法
     * @throws BizException   会员不存在
     */
    MemberResp adjustLevel(MemberLevelAdjustReq req);

    /**
     * 沉睡会员识别:last_active_at 距今超过 days 天的会员(无活跃).
     * <p>分页参数由 {@code PageContextHolder} 提供.
     *
     * @param days 无活跃天数阈值(>=1)
     * @return 沉睡会员分页响应
     */
    PageResp<MemberResp> listSleeping(Integer days);
}
