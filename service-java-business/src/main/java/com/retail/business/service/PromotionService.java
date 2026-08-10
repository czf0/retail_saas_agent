package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.PromotionCreateReq;
import com.retail.business.dto.req.PromotionQueryReq;
import com.retail.business.dto.req.PromotionUpdateReq;
import com.retail.business.dto.resp.PromotionCreateResp;
import com.retail.business.dto.resp.PromotionDeleteResp;
import com.retail.business.dto.resp.PromotionListItemResp;
import com.retail.business.dto.resp.PromotionResp;
import com.retail.business.dto.resp.PromotionUpdateResp;
import com.retail.business.dto.resp.ProductPromotionItemResp;
import com.retail.business.entity.Promotion;

import java.util.List;

/**
 * 促销活动服务.
 */
public interface PromotionService extends IService<Promotion> {

    /** 创建促销活动:校验时间,根据当前时间推断状态. */
    PromotionCreateResp createPromotion(PromotionCreateReq req);

    /** 分页查询促销活动,支持 status / targetType / keyword 过滤. */
    PageResp<PromotionListItemResp> listPromotions(Integer status, Integer targetType, String keyword);

    /**
     * 分页查询促销活动(业务语义过滤).
     * <p>
     * 在原有基础上额外支持活动类型,活动开始时间范围,结束时间范围过滤.
     */
    PageResp<PromotionListItemResp> listPromotions(PromotionQueryReq req);

    /** 促销活动详情. */
    PromotionResp getPromotion(Long promotionId);

    /** 部分更新促销活动. */
    PromotionUpdateResp updatePromotion(Long promotionId, PromotionUpdateReq req);

    /**
     * 启用促销活动: 将状态置为进行中 (ACTIVE).
     * <p>前置条件: 活动必须存在, 否则抛 BizException; 已为 ACTIVE 时直接返回 (幂等).
     * <p>副作用: 启用后活动开始生效, 参与商品的价格与门槛按活动规则计算.
     */
    PromotionUpdateResp enablePromotion(Long promotionId);

    /**
     * 停用促销活动: 将状态置为未开始 (PENDING), 使活动暂停生效.
     * <p>前置条件: 活动必须存在, 否则抛 BizException.
     * <p>副作用: 停用后活动规则暂停, 已创建的订单不受影响.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    PromotionUpdateResp disablePromotion(Long promotionId);

    /**
     * 提前结束促销活动: 将状态置为已结束 (EXPIRED), 并将结束时间置为当前时间.
     * <p>前置条件: 活动必须存在, 否则抛 BizException.
     * <p>副作用: 结束后活动不可再恢复, 永久终结; 后续下单不再应用该活动规则.
     * <p>破坏性: Agent 工具调用路径须 destructive=true 触发 HITL (铁律 19).
     */
    PromotionUpdateResp endPromotion(Long promotionId);

    /** 逻辑删除促销活动. */
    PromotionDeleteResp deletePromotion(Long promotionId);

    /** 查询商品参与的活动(仅 active 状态). */
    List<ProductPromotionItemResp> getProductPromotions(Long productId);
}
