package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 商品盘点操作结果响应(仓库/门店清点后提交);返回账面/实盘/盘差及最终调整后库存(Agent/HITL 组织为"盘盈 N 件/盘亏 M 件"结论).
 * <p>盘点后自动写一条 stock_movement 流水:盘盈 → check_gain(+),盘亏 → check_loss(-).
 */
@Data
public class StockCountResp {

    /** 是否成功 */
    private Boolean success;

    /** 提示信息 */
    private String message;

    /** 商品名称 */
    private String productName;

    /** 账面数量 (盘点基准, 系统当前可用库存) */
    private Integer bookQty;

    /** 实盘数量 (用户填报) */
    private Integer actualQty;

    /** 盘差 = actualQty - bookQty */
    private Integer diff;

    /** 结果类型: 盘盈 / 盘亏 / 平账 */
    private String resultType;

    /** 调整后可用库存 */
    private Integer afterQty;
}
