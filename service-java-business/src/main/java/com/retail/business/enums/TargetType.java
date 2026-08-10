package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 促销 / 优惠券适用目标范围枚举; promotion_template.target_type + coupon_template.target_type.
 * <p>促销引擎匹配活动时用于过滤适用商品的范围:
 * <ul>
 *   <li>ALL(1 全部商品): 促销适用于整个商品目录; 无需额外过滤; 范围最广.</li>
 *   <li>PRODUCT(2 指定商品): 指定 product_ids 白名单; 促销仅对列出的 SKU 生效; target_rel 表存储列表.</li>
 *   <li>CATEGORY(3 指定分类): 指定 category_ids 白名单 + 递归子分类; 列出分类下的所有 SKU 均生效; target_rel 表存储列表.</li>
 * </ul>
 */
public enum TargetType implements BaseEnum {

    /** 全部商品目标范围; 促销覆盖整个商品目录; 无需 target_rel 行; 资格最宽泛(可能受 exclude_group / 叠加规则限制). */
    ALL(1, "全部"),
    /** 指定商品白名单范围; 显式列出可参与的 product_ids; target_rel 表存储(promotion_id, product_id)对; 仅列出的商品生效, 不应用分类继承. */
    PRODUCT(2, "商品"),
    /** 指定分类白名单范围; 显式列出 category_ids 并含递归子分类; 列出分类下所有商品均可参与; 新商品若归入所列分类则自动包含. */
    CATEGORY(3, "分类");

    @EnumValue
    private final Integer code;
    private final String desc;

    TargetType(Integer code, String desc) {
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
