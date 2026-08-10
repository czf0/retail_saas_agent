package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品创建操作结果响应;继承通用 OperationResultResp.success+message,额外返回新生成的 productId 供前端跳转详情页.
 * <p>继承 success/message 见 {@link com.retail.business.dto.OperationResultResp}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductCreateResp extends OperationResultResp {

    /** 新创建 SPU 的主键(product_info.id;success=true 时非空). */
    private Long productId;
}
