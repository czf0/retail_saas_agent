package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.MemberLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员主实体, 对应数据库 member 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(会员为租户全局账户, 跨门店消费累计).
 * <p>业务约束: 无 deleted 字段(不软删除), 账号冻结通过 status 字段控制(暂未启用, 保留扩展); 升级规则 = 累计消费 OR 累计积分触发(MemberLevel 枚举定义阈值), Service 异步写入; 手动调级仅通过 MemberLevelAdjustReq(运营审批流).
 * <p>唯一约束: UNIQUE(tenant_id, phone), 同一租户下手机号不可重复(手机号即登录账号).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String phone;

    /** 会员等级(MemberLevel 枚举本体: 1=ORDINARY 普通, 2=SILVER 银卡, 3=GOLD 金卡, 4=DIAMOND 钻石); 升级规则 = 累计消费 OR 累计积分触发, Service 异步写入; 手动调级仅通过 MemberLevelAdjustReq(运营审批流); 积分倍率: 普通=1.00, 银卡=1.20, 金卡=1.50, 钻石=2.00. */
    private MemberLevel level;

    /** 当前可用积分余额(消费时可抵扣, 1 积分 = 0.01 元; 扣减顺序: 先到期先扣 FIFO, by points_log 明细). */
    private Integer points;

    /** 累计消费金额(订单完成时累加, 实付金额 pay_amount, 退款时不减, 用于会员升级触发; 单位: 元, 精度: 分, DECIMAL(12,2)). */
    private BigDecimal totalSpent;

    /** 累计订单数(订单完成时由 OrderServiceImpl 调用 MemberMapper.incTotalOrders 增量更新); 退款订单不减, 用于会员升级触发. */
    private Integer totalOrders;

    /** 最后下单时间(Asia/Shanghai 时区, 订单状态变为 COMPLETED 时同步更新); 用于 RFM 报表 Recency 维度 = now - lastOrderAt. */
    private LocalDateTime lastOrderAt;

    /** 最后活跃时间(Asia/Shanghai 时区, 触发事件: 下单/积分变动/券核销/登录/标签变动); 用于客户活跃度报表: 30 天内 = 活跃, 90 天内 = 沉睡, 180 天外 = 流失. */
    private LocalDateTime lastActiveAt;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
