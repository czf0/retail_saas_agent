package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 库存流水查询工具(stock:movement, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>商品定位: 支持 productId/productName/skuCode 多维自然语言解析, 不只依赖 productId(铁律 20).
 */
@Data
public class StockMovementToolReq {

    /** 目标商品 id, 对应 product.id; 可空=不限. */
    private Long productId;

    private String productName;

    private String skuCode;

    private String storeName;

    /** MovementType 枚举 code: 1=INBOUND 入库 2=OUTBOUND 出库 3=ADJUST 手动调整 4=RESERVATION 锁定 5=RELEASE 释放 6=CHECK_GAIN 盘盈 7=CHECK_LOSS 盘亏; 可空. */
    private Integer movementType;

    /** StockBizType 枚举 code: 1=ORDER 订单 2=PURCHASE 采购 3=ADJUST 调整 4=REFUND 退款 5=MANUAL 手工; 可空. */
    private Integer bizType;

    private String bizNo;

    /** 流水起始时间(含, Asia/Shanghai); 格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss; 可空. */
    private String startDate;

    /** 流水结束时间(含, Asia/Shanghai); 格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss; 可空. */
    private String endDate;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
