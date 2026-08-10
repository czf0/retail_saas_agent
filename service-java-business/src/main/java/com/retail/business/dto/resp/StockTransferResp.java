package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 门店间调拨操作结果响应;包含调拨是否成功 + 源/目标门店调拨后可用库存快照 + 调拨单号(Agent/HITL 结论展示用).
 * <p>原子性:源门店出库 + 目标门店入库在同一本地事务;失败则两边库存都不扣不加(无中间状态,避免库存差异).
 */
@Data
public class StockTransferResp {

    /** 是否成功 */
    private Boolean success;

    /** 提示信息 */
    private String message;

    /** 商品名称 */
    private String productName;

    /** 源门店名称 */
    private String fromStoreName;

    /** 目标门店名称 */
    private String toStoreName;

    /** 调拨数量 */
    private Integer qty;

    /** 源门店调拨后可用库存 */
    private Integer fromAfterQty;

    /** 目标门店调拨后可用库存 */
    private Integer toAfterQty;

    /** 调拨单号 (TRANSFER + 时间戳, 源出库/目标入库两笔流水共用) */
    private String transferNo;
}
