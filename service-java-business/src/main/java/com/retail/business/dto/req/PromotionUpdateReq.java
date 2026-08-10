package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 更新促销活动请求,全部字段可空(部分更新).
 */
@Data
public class PromotionUpdateReq {
    private String name;
    /** pending / active / expired */
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> rules;
}
