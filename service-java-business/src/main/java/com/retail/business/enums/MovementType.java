package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 库存出入库变动类型枚举; 映射 stock_movement.movement_type 列.
 * <p>每种变动类型对应库存 available / locked / deducted 字段的具体增减方向:
 * <ul>
 *   <li>INBOUND(1 入库): 采购单 / 退货入库; available+ 实际入; biz_type 通常为 PURCHASE.</li>
 *   <li>OUTBOUND(2 出库): 销售订单付款确认后发货; available- deducted+; biz_type 通常为 ORDER.</li>
 *   <li>ADJUST(3 手动调整): 运营盘点后手动更正; 有符号 delta; biz_type 通常为 ADJUST, 正负号决定方向.</li>
 *   <li>RESERVATION(4 锁定): 待付款订单锁定库存; available- locked+; 取消或超时释放.</li>
 *   <li>RELEASE(5 释放): 取消订单 / 支付超时释放锁定库存; available+ locked-; 反向抵消 RESERVATION.</li>
 *   <li>CHECK_GAIN(6 盘盈): 盘点实存 > 系统账存; available+ 实际增加; biz_type 通常为 ADJUST(+delta).</li>
 *   <li>CHECK_LOSS(7 盘亏): 盘点实存 < 系统账存; available- 实际减少; biz_type 通常为 ADJUST(-delta).</li>
 * </ul>
 */
public enum MovementType implements BaseEnum {

    /** 入库变动; 采购单收货, 商品入库; stock available+ 增加; 创建 stock_movement 正向 qty delta 并引用采购单号. */
    INBOUND(1, "入库"),
    /** 出库变动; 销售订单付款后发货出库; stock available- 减少 + deducted+ 增加; 创建 stock_movement 负向 qty delta 并引用订单号. */
    OUTBOUND(2, "出库"),
    /** 运营手动库存调整; 手动录入库存 delta 正负号, 正增负减双向均可; 用于盘点差异发现后的数量校正. */
    ADJUST(3, "手动调整"),
    /** 待付款订单锁定/预留; stock available- 减少 locked+ 增加; 冻结不可供其他结算使用; 取消/超时通过 RELEASE 反向抵消. */
    RESERVATION(4, "锁定"),
    /** 释放锁定预留; stock available+ 增加 locked- 减少; 订单取消或 30 分钟支付超时触发; 反向抵消 RESERVATION 的 delta. */
    RELEASE(5, "释放"),
    /** 盘点盈余; 实际清点数量大于系统记录数; stock available+ 增加; 盘点审核通过后登记. */
    CHECK_GAIN(6, "盘盈"),
    /** 盘点亏损; 实际清点数量小于系统记录数; stock available- 减少; 盘点审核通过后登记. */
    CHECK_LOSS(7, "盘亏");

    @EnumValue
    private final Integer code;
    private final String desc;

    MovementType(Integer code, String desc) {
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
