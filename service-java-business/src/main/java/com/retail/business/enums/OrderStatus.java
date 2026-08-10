package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

import java.util.EnumSet;

/**
 * 订单状态枚举; code = 1 待付款, 2 已付款, 3 已发货, 4 已完成, 5 已关闭, 6 退款中, 7 已退款.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>PENDING(1) → PAID(2): 用户在线支付成功(三方回调, trade_no 匹配); 未支付超时 30 分钟 → CLOSED(5).</li>
 *   <li>PAID(2) → SHIPPED(3): 仓库出库(手动或自动), 扣减库存 available 字段(locked→deducted 实际扣减).</li>
 *   <li>SHIPPED(3) → COMPLETED(4): 用户确认收货, 或超时 7 天自动确认(系统定时任务).</li>
 *   <li>PAID(2) / SHIPPED(3) / COMPLETED(4) → REFUNDING(6): 提交退款申请, 订单锁定待审核.</li>
 *   <li>REFUNDING(6) → REFUNDED(7): 全额退款审核通过并处理完成; 库存回补 + pay_flow 反向流水 + 优惠券未过期则退回.</li>
 *   <li>REFUNDING(6) → PAID(2)/SHIPPED(3)/COMPLETED(4): 退款申请拒绝, 回滚至原状态, 订单正常流转.</li>
 *   <li>PENDING(1) → CLOSED(5): 用户主动取消, 或定时任务超时未支付; locked 库存还原为 available(释放锁定).</li>
 * </ol>
 * <p>终态(不可逆): COMPLETED / CLOSED / REFUNDED; 其余中间态均可被售后流程修改.
 */
public enum OrderStatus implements BaseEnum {

    /** 订单已创建(购物车结算成功未支付); 库存字段 locked 增加(锁定可用库存); 财务无流水; 30 分钟未支付自动取消. */
    PENDING(1, "待付款"),
    /** 已支付(三方回调 trade_no 已匹配); 库存 locked→deducted(实际扣减); 财务 pay_amount 已入账; 此状态不可直接取消, 仅可走售后退款. */
    PAID(2, "已付款"),
    /** 已发货(仓库出库完成, 已录入快递单号); 库存 deducted 不回补; 签收前可走部分/全额退款. */
    SHIPPED(3, "已发货"),
    /** 已完成(用户确认收货或 7 天超时自动确认); 终态不可逆; 30 天内允许售后; 积分/等级/销售统计在此节点计入. */
    COMPLETED(4, "已完成"),
    /** 已关闭(用户主动取消或超时); 库存 locked 还原为 available(释放锁定); CLOSED 为终态不可逆, 不可回退至 PENDING. */
    CLOSED(5, "已关闭"),
    /** 退款中(退款申请提交待审核); 订单锁定, 不可发货/确认; 若审核拒绝可回滚至 PAID/SHIPPED/COMPLETED. */
    REFUNDING(6, "退款中"),
    /** 已退款(全额退款审核通过并处理完成); 终态不可逆; 库存全额回补; pay_flow 写入反向流水; 优惠券未过期则退回. */
    REFUNDED(7, "已退款");

    @EnumValue
    private final Integer code;
    private final String desc;

    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    /** 可支付状态集合: 仅 PENDING */
    public static final EnumSet<OrderStatus> PAYABLE = EnumSet.of(PENDING);
    /** 可关闭状态集合: 仅 PENDING */
    public static final EnumSet<OrderStatus> CLOSABLE = EnumSet.of(PENDING);
    /** 可发货状态集合: 仅 PAID */
    public static final EnumSet<OrderStatus> SHIPPABLE = EnumSet.of(PAID);
    /** 可退款状态集合: 已付款/已发货/已完成(退款金额不超过实付金额) */
    public static final EnumSet<OrderStatus> REFUNDABLE = EnumSet.of(PAID, SHIPPED, COMPLETED);

    /**
     * 校验状态流转是否合法.
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return true 表示流转合法
     */
    public static boolean canTransit(OrderStatus current, OrderStatus target) {
        if (current == null || target == null) {
            return false;
        }
        if (current == target) {
            return false;
        }
        if (current == PENDING && (target == PAID || target == CLOSED)) {
            return true;
        }
        if (current == PAID && target == SHIPPED) {
            return true;
        }
        if (current == SHIPPED && target == COMPLETED) {
            return true;
        }
        if (REFUNDABLE.contains(current) && target == REFUNDING) {
            return true;
        }
        if (current == REFUNDING && target == REFUNDED) {
            return true;
        }
        if (current == REFUNDING && REFUNDABLE.contains(target)) {
            return true;
        }
        return false;
    }

    /**
     * 判断订单是否处于已付款类状态(含已发货/已完成/退款中, 不含已退款).
     */
    public static boolean isPaidStatus(OrderStatus status) {
        return REFUNDABLE.contains(status) || status == REFUNDING;
    }
}
