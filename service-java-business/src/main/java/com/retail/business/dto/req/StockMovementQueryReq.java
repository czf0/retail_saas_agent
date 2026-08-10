package com.retail.business.dto.req;

import lombok.Data;

/**
 * 库存流水查询请求.
 * <p>
 * 仅承载业务过滤字段;分页参数(page / pageSize)由 {@code PageParameterInterceptor} 从
 * {@code HttpServletRequest} 提取注入 {@code PageContextHolder},Agent 工具路径手动注入,
 * 本类不再承载分页参数.日期参数支持 "yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm:ss" 两种格式(由 Service 层解析).
 */
@Data
public class StockMovementQueryReq {

    /** 商品ID */
    private Long productId;

    /** 变动类型:inbound/outbound/adjust/reservation/release/check_gain/check_loss */
    private Integer movementType;

    /** 业务类型:order/purchase/adjust/refund/manual */
    private Integer bizType;

    /** 关联单据号 */
    private String bizNo;

    /** 起始时间(含),"yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm:ss" */
    private String startDate;

    /** 结束时间(含),"yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm:ss" */
    private String endDate;
}
