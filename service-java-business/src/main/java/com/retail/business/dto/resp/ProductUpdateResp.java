package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品修改操作结果响应;继承通用 OperationResultResp.success+message,额外返回受影响行数(乐观锁版本冲突校验).
 * <p>继承 success/message 见 {@link com.retail.business.dto.OperationResultResp}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductUpdateResp extends OperationResultResp {

    /** 受影响行数(=1 成功;=0 版本冲突/记录已删,前端提示"数据已过时请刷新"). */
    private Long updated;
}
