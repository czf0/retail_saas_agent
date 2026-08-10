package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 订单类型枚举; code = 1 正常订单, 2 闪购订单, 3 秒杀订单.
 * <p>决定促销资格, 库存扣减策略, 订单级展示 UI:
 * <ul>
 *   <li>NORMAL(1 正常订单): 标准购物车结算; 可叠加优惠券 + 促销; 常规库存锁定 30 分钟.</li>
 *   <li>QUICK(2 闪购订单): 商品详情页一键立即购; 跳过购物车; 可参与促销; 支付超时 15 分钟.</li>
 *   <li>FLASH_SALE(3 秒杀订单): 限时秒杀活动订单; 固定价不可叠加; 独立 flash_sale_stock 库存池(与常规库存隔离); 支付超时 5 分钟.</li>
 * </ul>
 */
public enum OrderType implements BaseEnum {

    /** 正常订单; 标准购物车多商品结算; 可叠加优惠券 + 促销叠加; 支付超时 30 分钟后自动取消. */
    NORMAL(1, "正常订单"),
    /** 闪购订单; 商品详情页单 SKU 一键下单, 不经过购物车; 可参与促销; 支付超时 15 分钟(短于正常订单). */
    QUICK(2, "闪购订单"),
    /** 限时秒杀订单; 独立于常规库存的 flash_sale_stock 池; 按活动规则禁用优惠券/促销叠加; 支付超时严格 5 分钟. */
    FLASH_SALE(3, "秒杀订单");

    @EnumValue
    private final Integer code;
    private final String desc;

    OrderType(Integer code, String desc) {
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
