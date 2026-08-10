package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 退款单状态枚举; code = 1 待审核, 2 审核通过, 3 审核拒绝, 4 已退款, 5 已撤销.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>PENDING(1) → APPROVED(2): 售后审核通过; 生成库存回补计划 + 确认部分/全额扣减方案.</li>
 *   <li>APPROVED(2) → REFUNDED(4): 财务处理退款成功(三方退款回调); 仅正向退款成功后写入 pay_refund 流水.</li>
 *   <li>PENDING(1) → REJECTED(3): 售后审核拒绝(证据不足 / 不符合政策); 通知用户, 可提交新的退款单.</li>
 *   <li>PENDING(1) → CANCELLED(5): 用户审核前主动撤销退款申请; 恢复原订单状态.</li>
 *   <li>APPROVED(2) → REJECTED(3): 财务处理前二次审核拒绝; 罕见场景, 回滚审批结果.</li>
 * </ol>
 * <p>终态(不可逆): REJECTED / REFUNDED / CANCELLED; APPROVED 可流转至 REFUNDED 或 REJECTED.
 */
public enum RefundStatus implements BaseEnum {

    /** 待审核(用户提交退款单); 库存尚未回补; 财务无流水; 超时 48 小时未审核自动升级至主管处理. */
    PENDING(1, "待审核"),
    /** 审核通过(待退款); 库存标记待回补(退款成功后 available+=); 财务退款待处理; 财务回调后下一状态 → REFUNDED. */
    APPROVED(2, "审核通过"),
    /** 审核拒绝(终态); 库存不变; 财务无流水; 用户可修改证据后提交 NEW 退款单(不可复用当前退款单). */
    REJECTED(3, "审核拒绝"),
    /** 已退款成功(终态); 库存 available+= 实际回补; 财务 pay_refund 流水写入 + pay_amount 反向入账; 优惠券未过期则退回. */
    REFUNDED(4, "已退款"),
    /** 用户已撤销(终态); 库存不变; 财务无流水; 原订单按原流程继续流转(发货/完成等). */
    CANCELLED(5, "已撤销");

    @EnumValue
    private final Integer code;
    private final String desc;

    RefundStatus(Integer code, String desc) {
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
}
