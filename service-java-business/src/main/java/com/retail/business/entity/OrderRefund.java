package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.RefundStatus;
import com.retail.business.enums.RefundType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体, 对应数据库 order_refund 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解).
 * <p>业务约束: 物理删除表(无 deleted 字段); 退款流程: 申请 pending -> 审核 approved/rejected -> 执行 refunded; 审核通过时同事务联动: 退券 + 退积分 + 库存回滚 + 订单 refund_amount 累加.
 * <p>唯一约束: UNIQUE(tenant_id, refund_no), 同一租户下退款单号不可重复; 同一订单可多次部分退款, 但 refund_amount 累加不得超过 order_info.pay_amount.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("order_refund")
public class OrderRefund {

    @TableId(type = IdType.AUTO)
    private Long id;
        private Long tenantId;
        private Long storeId;

    /** 退款单号(UNIQUE(tenant_id, refund_no), 租户内唯一); 生成规则: RF + yyyyMMddHHmmss + 4 位随机数, 共 20 位; 财务对账单主键. */
    private String refundNo;

    /** 原订单 id, 指向 order_info.id; 退款操作前必须 SELECT order_info FOR UPDATE 锁定订单行, 避免并发多笔退款超 pay_amount. */
    private Long orderId;

    /** 冗余原订单号(避免退款列表查询 JOIN order_info); 订单号变更场景极少, 且即使变更也保留历史快照值. */
    private String orderNo;

    /** 会员 id(从订单 member_id 冗余写入); 退积分时通过此值关联会员账户; NULL=散客订单退款(不退积分不退券). */
    private Long memberId;

    /** 退款类型(RefundType 枚举本体: 1=FULL 全额, 2=PARTIAL 部分); FULL 时 refundQty 默认 = 订单全部 qty 之和, PARTIAL 需手动指定 item 明细. */
    private RefundType refundType;

    /** 退款金额(单位: 元, 精度: 分, DECIMAL(12,2)); 上限校验: 不能超过 order_info.pay_amount - SUM(历史已退 refund_amount); Service 层加锁后校验. */
    private BigDecimal refundAmount;

    /** 退款商品件数(PARTIAL 部分退款时必填, FULL 时 = NULL); 用于库存回滚数量计算 + 展示退款明细比例. */
    private Integer refundQty;

    /** 退款原因(用户/运营填写, 预设枚举: 质量问题/尺寸不符/不想要了/其他, 下拉选择 + 自定义文本拼接). */
    private String reason;

    /** 退款状态(RefundStatus 枚举本体: 1=PENDING 待审, 2=APPROVED 通过, 3=REJECTED 拒绝, 4=REFUNDED 已退款); 状态机由 OrderRefundServiceImpl 校验. */
    private RefundStatus status;

    /** 申请时间(Asia/Shanghai 时区, 退款单创建时写入); 运营 SLA: 待审退款 24h 内必须处理, 超时自动 APPROVED. */
    private LocalDateTime applyTime;

    /** 实际退款完成时间(Asia/Shanghai 时区, status 变为 REFUNDED 时写入); 三方退款成功回调时同步更新此值. */
    private LocalDateTime refundTime;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
