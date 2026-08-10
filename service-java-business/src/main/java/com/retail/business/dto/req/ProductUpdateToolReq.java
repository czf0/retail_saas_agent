package com.retail.business.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品更新 Agent 工具入参.
 * <p>
 * 继承 {@link ProductUpdateReq} 复用全部可更新字段, 追加 productId 定位商品.
 * Jackson introspect 会扫描继承链, Schema 包含父类全部属性.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateToolReq extends ProductUpdateReq {

    /** 商品 ID (定位待更新商品) */
    private Long productId;
}
