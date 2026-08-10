package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板创建请求(运营后台营销管理 -> 新增优惠券模板).
 * <p>对应 Controller 路由: POST /api/v1/coupon/templates; status 字段 Service 层赋默认值(铁律 6),
 * CreateReq 不承载 status.
 * <p>如涉及 Agent 工具破坏性操作(删除/上下架/调价/出入库等), Service 层触发 HITL(destructive=true, 铁律 19).
 */
@Data
public class CouponTemplateCreateReq {
    private String name;
    /** CouponType 枚举 code: 1=FULLCUT 满减 2=DISCOUNT 折扣 3=CASH 代金券; 折扣券时 faceValue 为折扣率. */
    private Integer type;
    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp), 数据库 DECIMAL(12,2); type=FULLCUT/CASH 时为减免金额; type=DISCOUNT 时为折扣率(如 0.80 表示 8 折). */
    private BigDecimal faceValue;
    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 满减门槛最低实付金额(不含运费), 0=无门槛. */
    private BigDecimal threshold;
    /** ValidType 枚举 code: 1=RELATIVE 领取后 N 天 2=FIXED 固定起止时间; 与 validDays/validStart+validEnd 互斥校验. */
    private Integer validType;
    /** validType=RELATIVE 时领取后有效天数; 与 validStart+validEnd 互斥(Service 层校验). */
    private Integer validDays;
    /** 有效期-起(含当日, Asia/Shanghai 00:00:00 截断); validType=FIXED 时必填. */
    private LocalDateTime validStart;
    /** 有效期-止(含当日, Asia/Shanghai 23:59:59.999); validType=FIXED 时必填, 必须晚于 validStart. */
    private LocalDateTime validEnd;
    /** 发放总张数; 0=不限制(运营大促场景), 默认 0; 超限时前端提示"已领完", Service 层抛 ParamException. */
    private Integer totalCount;
    /** 每会员限领张数; 0=不限制, 默认 1; 超限时前端提示"您已达领券上限", Service 层抛 ParamException. */
    private Integer perLimit;
    /** 关联促销活动 id, 对应 promotion.id; 软关联, 可空. */
    private Long promotionId;
}
