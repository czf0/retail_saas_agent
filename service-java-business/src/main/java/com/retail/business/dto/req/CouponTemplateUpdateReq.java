package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新优惠券模板请求,全部字段可空(部分更新).
 * <p>支持修改名称,状态,发放总量,每人限领,fixed 结束时间,使用门槛;
 * 面额/类型/有效期类型不支持变更(涉及已发放券的核销金额一致性,禁止改).
 */
@Data
public class CouponTemplateUpdateReq {
    private String name;
    /** 状态:active 启用 / inactive 停用 */
    private Integer status;
    /** 发放总量,0=不限 */
    private Integer totalCount;
    /** 每人限领 */
    private Integer perLimit;
    /** fixed 模式下的结束时间(仅 fixed 有效期类型可改) */
    private LocalDateTime validEnd;
    /** 使用门槛(满 X 元可用,0 表示无门槛);可调以适应运营节奏,不影响已发放券面额 */
    private BigDecimal threshold;
}
