package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 促销活动删除操作结果响应(软删除,deleted=1);继承通用 OperationResultResp.success+message,返回受影响行数.
 * <p>注意:进行中(status=ONGOING)且已关联有效订单/券的活动不可删除(返回 success=false,message=先停用后删除).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromotionDeleteResp extends OperationResultResp {
    /** 成功软删除行数(=1 成功;=0 不存在/已删;>1 异常批量删除,日志告警). */
    private Long deleted;
}
