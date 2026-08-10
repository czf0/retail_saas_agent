package com.retail.business.dto.resp;

import lombok.Data;

import java.util.Map;

/**
 * 商品关联的进行中促销活动项(商品详情页"正在进行的活动"标签组 / 订单结算页"可享优惠"列表);单 SPU 可同时命中多条活动,按权重排序取最优.
 * <p>计算字段 rules=活动规则摘要(活动中心前端直接渲染"满 300 减 50"),无需再查完整 PromotionResp.
 */
@Data
public class ProductPromotionItemResp {
    /** 活动外键(promotion_info.id;点击可跳转活动详情). */
    private Long id;
    /** 活动名称(如"夏季满减"). */
    private String name;
    /** 活动类型枚举:1=满减 2=折扣 3=满赠 4=发券 5=买赠;见 PromotionTypeEnum. */
    private Integer type;
    /** 规则动态 JSON(前端摘要显示,如满减 thresholds 阈值列表;简化版不包含全部完整字段). */
    private Map<String, Object> rules;
}
