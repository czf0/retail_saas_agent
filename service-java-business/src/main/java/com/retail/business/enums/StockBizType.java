package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 库存变动业务来源类型枚举; stock_movement.biz_type 列.
 * <p>标识触发库存变动的上游业务模块; 用于跨模块审计追溯和对账:
 * <ul>
 *   <li>ORDER(1 订单业务): 销售订单发货出库 + 退款退库入库; movement_type 通常为 OUTBOUND / INBOUND(退款).</li>
 *   <li>PURCHASE(2 采购入库): 采购单仓库收货; movement_type 通常为 INBOUND; 关联 purchase_order_id.</li>
 *   <li>ADJUST(3 手动调整): 盘点差异手动更正; movement_type 通常为 ADJUST; 有符号 delta.</li>
 *   <li>REFUND(4 退款回滚): 订单退款商品退回至库存; movement_type 通常为 INBOUND; 关联 refund_order_id.</li>
 *   <li>MANUAL(5 手工操作): 杂项运营操作兜底; movement_type 不固定; 记录运营 user_id.</li>
 * </ul>
 */
public enum StockBizType implements BaseEnum {

    /** 订单业务来源; 订单付款触发 OUTBOUND 扣减; 订单退款触发 INBOUND 回库; biz_ref_id 关联 order_id / refund_order_id. */
    ORDER(1, "订单业务"),
    /** 采购入库来源; 采购单仓库收货 INBOUND 变动; biz_ref_id 关联 purchase_order_id; 供应商批次号通常记录于 extra_json. */
    PURCHASE(2, "采购入库"),
    /** 手动调整来源; 运营盘点发现差异后发起的数量校正; ADJUST movement_type; 有符号 delta; 原因码存于 extra_json 审计链路. */
    ADJUST(3, "手动调整"),
    /** 退款回滚来源; 退款审核通过后商品退回至 available; INBOUND movement_type; biz_ref_id 关联 refund_order_id; 确保库存与退款支付对账. */
    REFUND(4, "退款回滚"),
    /** 手工操作兜底; 上述特定 biz_type 未覆盖的杂项运营动作; 后台库存工具操作的默认值; biz_ref_id 可为 null. */
    MANUAL(5, "手工操作");

    @EnumValue
    private final Integer code;
    private final String desc;

    StockBizType(Integer code, String desc) {
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
