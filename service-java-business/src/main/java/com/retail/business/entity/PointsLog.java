package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.PointsBizType;
import com.retail.business.enums.PointsChangeType;

import java.time.LocalDateTime;

/**
 * 会员积分流水实体, 对应数据库 points_log 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); points_log 已加入 application.yml store.tables 白名单, 由 StoreLineHandler 处理.
 * <p>业务约束: 流水为不可变历史记录(物理删除表, 无 deleted 字段; 仅 created_at + createBy, 无更新); beforeBalance / afterBalance 为余额快照, 便于审计追溯(即使 member.points 后续变更, 流水快照保持不变).
 * <p>幂等约束: UNIQUE(tenant_id, biz_type, biz_no), 同一业务单据只能产生 1 条积分流水(避免重复加/扣积分).
 */
@Data
@TableName("points_log")
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;
        private Long tenantId;
        private Long storeId;

    /** 会员 id, 指向 member.id; 积分变动前必须 SELECT member FOR UPDATE 锁定会员行, 避免并发扣积分扣成负数. */
    private Long memberId;

    /** 变动类型(PointsChangeType 枚举本体: 1=EARN_ORDER 消费获取, 2=EARN_ACTIVITY 活动赠送, 3=SPEND_EXCHANGE 兑换消耗, 4=DEDUCT_REFUND 退款扣减, 5=ADJUST_MANUAL 手动调整); 类型决定 UI 展示图标/文案. */
    private PointsChangeType changeType;

    /** 变动积分值(整数); 正数 = 积分增加, 负数 = 积分扣减; 订单获取 = FLOOR(pay_amount * pointsRate), 退款扣减 = -FLOOR(退款金额 * pointsRate). */
    private Integer changePoints;

    /** 变动前积分余额快照(写入前从 member.points 读取); 便于审计追溯: beforeBalance + changePoints = afterBalance, 恒等式校验保证流水正确. */
    private Integer beforeBalance;

    /** 变动后积分余额快照(写入后同步写 member.points = afterBalance); 与 member.points 最终一致, 但快照永不改变(即使后续再变动). */
    private Integer afterBalance;

    /** 业务类型(PointsBizType 枚举本体: 1=ORDER 订单, 2=COUPON 优惠券, 3=MANUAL 手工, 4=ACTIVITY 活动); 与 biz_no 组合决定幂等去重键 UNIQUE(tenant, biz_type, biz_no). */
    private PointsBizType bizType;

    /** 关联业务单据号(如订单号 orderNo / 退款单号 refundNo / 活动编码 activityCode); 与 biz_type 组合唯一, 防止重复写流水(幂等). */
    private String bizNo;

    /** 流水备注(给会员看的说明, 如 "618 大促双倍积分赠送" / "订单 #2024060112 消费获取"); 积分中心列表展示用. */
    private String remark;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
