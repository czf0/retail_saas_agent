package com.retail.business.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类更新 Agent 工具入参.
 * <p>
 * 继承 {@link CategoryUpdateReq} 复用全部可更新字段, 追加 categoryId 定位分类.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryUpdateToolReq extends CategoryUpdateReq {

    /** 分类 ID (定位待更新分类) */
    private Long categoryId;
}
