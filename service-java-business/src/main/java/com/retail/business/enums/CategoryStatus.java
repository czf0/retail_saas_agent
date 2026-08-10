package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 商品分类启停状态枚举; code = 1 启用, 0 停用.
 * <p>用于 product_category.status 字段; 影响前台分类导航和商品列表过滤:
 * <ul>
 *   <li>ACTIVE(1 启用): 前台导航栏展示该分类; 分类下商品可搜索可购买.</li>
 *   <li>INACTIVE(0 停用): 前台隐藏该分类; 新商品不可选此分类; 分类下已有商品仅可通过搜索购买.</li>
 * </ul>
 * <p>子分类递归继承父级 INACTIVE 状态(即使子分类自身 ACTIVE 也递归隐藏).
 */
public enum CategoryStatus implements BaseEnum {

    /** 启用(前台导航展示); 分类下商品可列表展示可购买; 新建/编辑 SPU 时可分配此分类. */
    ACTIVE(1, "启用"),
    /** 停用(前台导航隐藏); 分类下商品仍可通过直接 SKU 链接访问; 新建 SPU 时不可选择该分类. */
    INACTIVE(0, "停用");

    @EnumValue
    private final Integer code;
    private final String desc;

    CategoryStatus(Integer code, String desc) {
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
