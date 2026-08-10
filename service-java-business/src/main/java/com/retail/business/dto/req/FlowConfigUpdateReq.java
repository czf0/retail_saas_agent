package com.retail.business.dto.req;

import lombok.Data;

import java.util.Map;

/**
 * 流程配置更新请求,全部字段可空.
 */
@Data
public class FlowConfigUpdateReq {

    private Map<String, Object> params;

    private Boolean enabled;
}
