package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品删除操作结果响应(软删除,deleted=1);继承通用 OperationResultResp.success+message,返回受影响行数.
 * <p>注意:已在 order_item 中被引用的历史商品不可硬删除;软删除仅影响前台在售列表不展示,后台仍可查历史.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDeleteResp extends OperationResultResp {

    /** 成功软删除行数(正常=1;=0 表示记录不存在/已删;>1 异常(批量),日志告警). */
    private Long deleted;
}
