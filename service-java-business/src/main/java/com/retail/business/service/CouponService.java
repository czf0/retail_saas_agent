package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.CouponIssueReq;
import com.retail.business.dto.req.CouponTemplateCreateReq;
import com.retail.business.dto.req.CouponTemplateQueryReq;
import com.retail.business.dto.req.CouponTemplateUpdateReq;
import com.retail.business.dto.resp.CouponIssueResp;
import com.retail.business.dto.resp.CouponTemplateListItemResp;
import com.retail.business.dto.resp.CouponTemplateResp;
import com.retail.business.entity.CouponTemplate;
import com.retail.core.dto.PageResp;

/**
 * 优惠券模板服务.
 * <p>负责模板生命周期管理(增删改查)与批量发放(issue).
 */
public interface CouponService extends IService<CouponTemplate> {

    /** 创建优惠券模板:状态默认 active,save 后返回详情. */
    CouponTemplateResp createTemplate(CouponTemplateCreateReq req);

    /** 部分更新模板:仅 name/status/totalCount/perLimit/validEnd 可改. */
    CouponTemplateResp updateTemplate(Long couponId, CouponTemplateUpdateReq req);

    /** 逻辑删除模板. */
    Boolean deleteTemplate(Long couponId);

    /**
     * 启用优惠券模板: 状态置为 ACTIVE, 恢复可发放/领取.
     * <p>前置条件: 模板必须存在, 否则抛 BizException; 状态为 ACTIVE 时直接返回 (幂等).
     * <p>副作用: 启用后会员可领取, 模板 issued_count 在发放时累加; 无跨模块调用.
     */
    CouponTemplateResp enableCoupon(Long couponId);

    /**
     * 停用优惠券模板: 状态置为 EXPIRED, 停止发放/领取 (对已发出券无影响).
     * <p>前置条件: 模板必须存在, 否则抛 BizException; 状态为 EXPIRED 时直接返回 (幂等).
     * <p>副作用: 停用后不可再发放/领取, 已发放的 user_coupon 仍可正常核销.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    CouponTemplateResp disableCoupon(Long couponId);

    /** 模板详情. */
    CouponTemplateResp getTemplate(Long couponId);

    /** 分页查询模板,支持 status / type / keyword 过滤. */
    PageResp<CouponTemplateListItemResp> listTemplates(Integer status, Integer type, String keyword);

    /**
     * 分页查询模板(业务语义过滤).
     * <p>
     * 在原有基础上额外支持面额区间,使用门槛区间,有效期固定起止范围过滤.
     */
    PageResp<CouponTemplateListItemResp> listTemplates(CouponTemplateQueryReq req);

    /**
     * 批量发放优惠券给指定会员 (事务保证: 模板 issued_count++ 与 user_coupon 创建同事务).
     * <p>前置条件: template 必须存在且状态为上架 (ON_SHELF), 否则抛 BizException; 单批上限 50 (铁律 12), 超限抛 ParamException.
     * <p>幂等: 同一 (templateId, memberId, batchNo) 由数据库 UNIQUE 去重, 重复请求返回"该会员已领取".
     * <p>副作用: 若开启"发券送积分"规则, 发券成功后异步事件触发会员积分增加 (非本事务); 对单个会员的失败不影响其他会员, 返回成功/失败计数.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     *
     * @param req 含 templateId + memberIds + batchNo (UUID, 用于幂等)
     * @return 发放结果 (成功/失败计数)
     * @throws ParamException 批量 > 50
     * @throws BizException   模板不存在 / 已下架
     */
    CouponIssueResp issue(CouponIssueReq req);
}
