package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销活动详情页展示响应;聚合活动规则配置 + 生效时间窗 + 适用对象 + 规则 JSON(动态表单渲染).
 * <p>Controller: GET /api/v1/promotions/{id:\\d+};{id} 正则守卫;未开始的活动仅管理员可见预览,会员不可见.
 */
@Data
public class PromotionResp {
    private Long id;

    /** 活动名称(后台管理列表展示 + 前台商品标签展示用,如"夏季满300减50"). */
    private String name;

    /** 促销类型:1=FULL_REDUCTION(满减) 2=DISCOUNT(折扣) 3=GIFT(满赠) 4=COUPON(发券触发活动) 5=BUY_N_GET_M(买N赠M);见 PromotionTypeEnum. */
    private Integer type;

    /** 适用对象类型:1=ALL(全场) 2=CATEGORY(指定分类) 3=PRODUCT(指定商品) 4=MEMBER_LEVEL(指定会员等级);见 PromotionTargetEnum. */
    private Integer targetType;

    /** 适用对象ID列表(targetType = PRODUCT/CATEGORY 时为 product_id/category_id;ALL 时为空列表). */
    private List<String> targetIds;

    /** 活动状态:0=NOT_STARTED(未开始) 1=ONGOING(进行中) 2=ENDED(已结束) 3=DISABLED(停用);计算字段(now vs start/end + 手动停用优先级高). */
    private Integer status;

    /** 活动开始时间(含,>= 才生效;精确到秒;时区 Asia/Shanghai). */
    private LocalDateTime startTime;

    /** 活动结束时间(不含,< 自动失效;与 startTime 左闭右开). */
    private LocalDateTime endTime;

    /** 规则动态配置(JSON Map;type=FULL_REDUCTION 含 thresholds=[{amount:300,discount:50},...],type=DISCOUNT 含 percent=85,具体见前端表单 schema). */
    private Map<String, Object> rules;

    private LocalDateTime createdAt;
}
