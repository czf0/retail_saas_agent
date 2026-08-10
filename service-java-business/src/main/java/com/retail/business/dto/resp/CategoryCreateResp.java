package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类创建操作结果响应;继承通用 OperationResultResp.success+message,额外返回新分类 categoryId 供前端刷新树选中节点.
 * <p>继承 success/message 见 {@link com.retail.business.dto.OperationResultResp}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoryCreateResp extends OperationResultResp {

    /** 新创建分类节点主键(product_category.id;success=true 时非空). */
    private Long categoryId;
}
