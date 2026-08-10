package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.CouponType;
import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.ValidType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体, 对应数据库 coupon_template 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(券模板为租户全局配置, 发券时按门店限制可选范围).
 * <p>业务约束: 模板定义券的面额/门槛/有效期等规则, 用户领券时 user_coupon 从模板冗余快照(couponName/faceValue/threshold/couponType)写入; 模板修改不影响已发出的历史券(快照隔离); promotionId 仅软关联 promotion 表, 不强制外键.
 * <p>唯一约束: UNIQUE(tenant_id, name), 同一租户下券模板名不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("coupon_template")
public class CouponTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 优惠券名称(UNIQUE(tenant_id, name), 租户内不可重复); 建议命名规则: 面额 + 门槛 + 范围, 如 "满 200 减 30 全场券" 便于运营识别. */
    private String name;

    /** 券类型(CouponType 枚举本体: 1=FULL_REDUCTION 满减券, 2=DISCOUNT 折扣券, 3=VOUCHER 代金券); 决定 faceValue 语义: 满减/代金券 = 金额元, 折扣券 = 折扣率(如 0.80 = 8 折). */
    private CouponType type;

    /** 面额值(BigDecimal, 精度由 type 决定); 满减/代金券单位: 元(如 30.00 = 减 30 元); 折扣券单位: 折扣率(如 0.80 = 8 折, 0 < faceValue < 1); 最低 0.01(不可免费). */
    private BigDecimal faceValue;

    /** 使用门槛金额(满 X 元可用, 单位: 元, 精度: 分, DECIMAL(12,2)); 0 = 无门槛(任意金额可用); 满减券要求 threshold > faceValue(否则无商业意义, Service 层校验). */
    private BigDecimal threshold;

    /** 有效期规则类型(ValidType 枚举本体: 1=RELATIVE 领取后 N 天有效, 2=FIXED 起止绝对时间); RELATIVE 用 validDays, FIXED 用 validStart + validEnd, 两组字段互斥非空. */
    private ValidType validType;

    /** 领取后有效天数(RELATIVE 类型必填, 正整数); 例如 validDays=7, 用户 6 月 1 日领取 -> 6 月 8 日 23:59:59 过期(Asia/Shanghai 时区). */
    private Integer validDays;

    /** 有效期起始时间(FIXED 类型必填, Asia/Shanghai 时区, 含此时间点); 促销定时发券场景, 券在 start 前不可用(status=ENABLED 也不行, 双重校验). */
    private LocalDateTime validStart;

    /** 有效期结束时间(FIXED 类型必填, Asia/Shanghai 时区, 含此时间点); 超过 validEnd 后券自动改为 EXPIRED 状态(定时任务扫描), 不可再核销. */
    private LocalDateTime validEnd;

    /** 发放总量上限(张数, 正整数); 0 = 不限量(实际受磁盘/租户配额限制); 发券时原子 UPDATE issuedCount + 1 WHERE issuedCount < totalCount, 超量返回发券失败. */
    private Integer totalCount;

    /** 已发放张数(已累计发出的券数, 含已使用/已过期/已退); 用于列表展示进度条 = issuedCount / totalCount, 运营可直观看到剩余库存. */
    private Integer issuedCount;

    /** 每人限领张数(活动周期内同一 memberId 领此模板券的上限); 0 = 不限制(防刷需风控, 不限量易被羊毛党薅); 建议默认 1(多数活动场景). */
    private Integer perLimit;

    /** 券模板状态(PromotionStatus 枚举本体: 1=PENDING 待启用, 2=ENABLED 进行中, 3=EXPIRED 已结束); 超过 validEnd 后定时任务自动改为 EXPIRED, EXPIRED 不可再发券. */
    private PromotionStatus status;

    /** 关联促销活动 id(软关联 promotion.id, 不强制外键); NULL = 独立券模板(不绑活动, 运营手动发券); 非空 = 活动联动券(活动开始自动发券/活动结束自动 EXPIRED). */
    private Long promotionId;
        private Integer deleted = 0;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
