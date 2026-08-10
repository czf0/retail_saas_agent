package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 库存人工调整操作结果响应(后台手动增加/扣减库存);返回调整后库存账户可用库存 + 生成的 stock_movement 流水ID(审计追溯).
 * <p>幂等:同一 adjustBatchNo 重复提交返回首次结果,不会重复写流水;调整原因必填(remark 记录在流水).
 */
@Data
public class StockAdjustResp {

    /** 是否成功 */
    private Boolean success;

    /** 提示信息 */
    private String message;

    /** 库存账户ID */
    private Long stockId;

    /** 调整后可用库存 */
    private Integer afterQty;

    /** 生成的库存流水ID */
    private Long movementId;
}
