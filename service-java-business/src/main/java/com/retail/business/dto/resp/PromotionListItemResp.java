package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销活动列表页行项(营销后台活动列表 / 前台"活动中心"列表,返回 20/页;点击进入详情 PromotionResp).
 * <p>targetNames(适用对象名称列表)为 Service 层 targetIds 批量回填;列表页直接展示避免前端"分类 #id"数据孤岛.
 */
@Data
public class PromotionListItemResp {
    private Long id;
    private String name;
    private Integer type;
    private Integer targetType;
    private List<String> targetIds;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> rules;

    /**
     * 适用对象名称列表(由 Service 批量解析回填).
     * <p>targetType=product 时存商品名称,=category 时存分类名称,=all 时存 ["全场商品"].
     * 消除前端列表仅显示 targetType 标签而看不到具体适用对象的数据孤岛.
     */
    private List<String> targetNames;
}
