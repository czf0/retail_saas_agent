package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 商品 SKU 上下架状态枚举; code = 0 下架, 1 上架.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>OFF_SHELF(0) → ON_SHELF(1): 运营单独上架 SKU, 或 SPU 上架时自动联动; 要求 stock available > 0; SKU 价格/属性需通过校验.</li>
 *   <li>ON_SHELF(1) → OFF_SHELF(0): 运营下架 SKU, 或 stock available=0 时自动下架(按租户可配置); 同 SPU 下其他 SKU 可保持上架.</li>
 * </ol>
 * <p>SKU 下架不触发 SPU 自动下架, 直至全部 SKU 均下架. 删除仅做逻辑删除(Entity.deleted=1).
 */
public enum SkuStatus implements BaseEnum {

    /** 上架(展示于 SPU SKU 选择器, 可购买); 库存 available 数量决定可否购买; 结算支付前锁定. */
    ON_SHELF(1, "上架"),
    /** 下架(SKU 选择器隐藏, 不可购买); 若后续补货可重新上架; 已下单的 order_item 快照仍有效. */
    OFF_SHELF(0, "下架");

    @EnumValue
    private final Integer code;
    private final String desc;

    SkuStatus(Integer code, String desc) {
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
