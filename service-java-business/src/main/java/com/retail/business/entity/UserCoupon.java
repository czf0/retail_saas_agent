package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.CouponStatus;
import com.retail.business.enums.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券(会员券资产)实体, 对应数据库 user_coupon 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); user_coupon 已加入 application.yml store.tables 白名单, 由 StoreLineHandler 处理; store_id=NULL=租户级领取(无门店上下文).
 * <p>业务约束: 会员领取的券资产实例(一券一行, 从 coupon_template 冗余快照 couponName/faceValue/threshold/couponType 写入, 模板修改不影响已发出券); 核销时更新 status + orderId + orderNo + usedTime, 退款时恢复 status=UNUSED 或置 REFUNDED(根据券有效期策略).
 * <p>唯一约束: UNIQUE(tenant_id, coupon_code), 单张券业务编码全局唯一(核销时通过 coupon_code 扫描/输入定位券).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 领取门店 id(StoreLineHandler 自动注入); NULL=租户级领取(无门店上下文的发券场景, 如会员日自动发券); 门店 POS 扫码领券场景写具体门店 id, 报表按门店维度统计发券/核销率. */
    private Long storeId;

    /** 所属券模板 id, 指向 coupon_template.id; 模板删除(逻辑删)时, 已发出的历史券保留可用(资产不回收, 快照隔离). */
    private Long couponId;

    /** 券名称冗余快照(领取时从 coupon_template.name 读取写入); 避免后续模板改名影响会员已收到的券显示(资产展示一致性). */
    private String couponName;

    /** 券类型冗余快照(CouponType 枚举本体, 领取时从 coupon_template.type 读取); 核销时计算折扣金额必须用此快照类型, 不能用模板实时值(防止模板改 type 套利). */
    private CouponType couponType;

    /** 所属会员 id, 指向 member.id; 券资产归属会员(不可转赠, 目前版本不支持转赠, 后续需新增 transfer_from_member_id 字段扩展). */
    private Long memberId;

    /** 券资产状态(CouponStatus 枚举本体: 1=UNUSED 未使用, 2=USED 已使用, 3=EXPIRED 已过期, 4=REFUNDED 已退回); 定时任务 ExpiredCouponJob 每日凌晨扫描, expireTime < now 的 UNUSED 券自动置 EXPIRED. */
    private CouponStatus status;

    /** 核销使用的订单 id, 指向 order_info.id; status=USED/REFUNDED 时非空, 用于关联订单 -> 券资产对账(避免一笔订单核销多券的 Bug 审计). */
    private Long orderId;

    /** 核销使用的订单号冗余(从 order_info.orderNo 读取写入); 会员券核销记录列表展示用, 避免 JOIN order_info 大表. */
    private String orderNo;

    /** 面额值冗余快照(领取时从 coupon_template.faceValue 读取写入, 单位: 元, 精度: 分); 核销时计算实际抵扣金额必须用此值(模板修改不影响已发出券). */
    private BigDecimal faceValue;

    /** 门槛金额冗余快照(领取时从 coupon_template.threshold 读取写入, 单位: 元, 精度: 分); 核销前置校验: 订单 pay_amount 必须 >= threshold, 否则不可核销用券. */
    private BigDecimal threshold;

    /** 领取成功时间(Asia/Shanghai 时区); 用户领券推送消息 + 领取记录列表排序用; 与 receiveTime + validDays 可计算 expireTime(RELATIVE 类型). */
    private LocalDateTime receiveTime;

    /** 核销使用时间(Asia/Shanghai 时区, status 变为 USED 时写入); 与 expireTime 比较若 > 则核销拒绝(必须先校验有效期再用券, 双重校验). */
    private LocalDateTime usedTime;

    /** 过期失效时间(Asia/Shanghai 时区, 含当天 23:59:59); RELATIVE 券 = receiveTime + validDays(领取时直接算出写入), FIXED 券 = coupon_template.validEnd 拷贝; 过了此时间券不可核销. */
    private LocalDateTime expireTime;
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
