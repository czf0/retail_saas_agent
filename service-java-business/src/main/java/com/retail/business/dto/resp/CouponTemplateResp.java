package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板详情页展示响应;聚合券模板基础信息 + 面额/门槛 + 有效期策略 + 发放总量/已发数/每人限领 + 绑定促销活动.
 * <p>Controller: GET /api/v1/coupons/{id:\\d+};{id} 正则守卫;用户可见"已过期"券但不可领(前端按钮置灰).
 */
@Data
public class CouponTemplateResp {
    private Long id;
    private String name;
    /** 券类型:1=FULL_CUT(满减) 2=DISCOUNT(折扣) 3=CASH(代金券/无门槛);见 CouponTypeEnum. */
    private Integer type;
    /** 面额/折扣值:type=CASH 时=固定金额(元);type=FULL_CUT 时=满减金额(元);type=DISCOUNT 时=折扣百分比 0.00~100.00(对应 70 = 7 折).单位: 元或百分比,取决于 type. */
    private BigDecimal faceValue;
    /** 使用门槛金额(type=FULL_CUT 时=满 N 元可用;type=DISCOUNT/CASH 时可为 0=无门槛).单位: 元,精度: 分. */
    private BigDecimal threshold;
    /** 有效期策略:1=RELATIVE(领取后 N 天有效,validDays 生效) 2=FIXED(固定起止日期段,validStart~validEnd 生效). */
    private Integer validType;
    /** RELATIVE 模式下领取后有效天数(正整数,如 7 = 7 天内有效);FIXED 模式该字段 NULL. */
    private Integer validDays;
    /** FIXED 模式下有效期起点(含,>=;精确到秒;Asia/Shanghai). */
    private LocalDateTime validStart;
    /** FIXED 模式下有效期终点(不含,<;左闭右开区间). */
    private LocalDateTime validEnd;
    /** 发放总数量(配置值;-1 表示不限量,前端展示"∞"). */
    private Integer totalCount;
    /** 已发放数量(COUNT(user_coupon);并发发券 Redis INCR 保证不超发). */
    private Integer issuedCount;
    /** 每人限领张数(per user per template;-1 表示不限制.正常=1~3 张,防止刷单). */
    private Integer perLimit;
    /** 模板状态:1=ACTIVE(启用,可发放) 0=INACTIVE(停用,不再发但已发券仍可用);见 CouponStatusEnum. */
    private Integer status;
    /** 绑定的促销活动外键(promotion.id;活动发券场景;NULL=运营手动/独立发券,不关联活动). */
    private Long promotionId;
    private LocalDateTime createdAt;
}
