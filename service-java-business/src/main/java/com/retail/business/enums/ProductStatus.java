package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 商品 SPU 上下架状态枚举; code = 0 下架, 1 上架.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>OFF_SHELF(0) → ON_SHELF(1): 运营后台手动点击上架; 校验所有必填字段(标题/价格/分类/图片)非空 + 至少 1 个 SKU 上架.</li>
 *   <li>ON_SHELF(1) → OFF_SHELF(0): 运营手动点击下架, 或系统自动下架(全部 SKU 库存为 0 / 分类被禁用 / 定时到期); 下架不影响已有订单(order_item 已快照).</li>
 * </ol>
 * <p>DRAFT 草稿态(不进枚举)存于 Entity.draft 标记位, 前台不展示. 删除仅做逻辑删除(Entity.deleted=1), 不做物理删除.
 */
public enum ProductStatus implements BaseEnum {

    /** 上架(前台展示, 可购买); 要求至少 1 个 SKU 处于上架态且分类 ACTIVE; 结算时锁定可用库存. */
    ON_SHELF(1, "上架"),
    /** 下架(前台隐藏, 不可购买); 后续可重新上架; 已下单的 order_item 快照不受影响, 订单仍可正常完成. */
    OFF_SHELF(0, "下架");

    @EnumValue
    private final Integer code;
    private final String desc;

    ProductStatus(Integer code, String desc) {
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
