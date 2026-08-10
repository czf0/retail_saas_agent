package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 租户更新请求,全部字段可空,仅传入字段参与更新.
 */
@Data
public class TenantConfigUpdateReq {

    private String tenantName;

    private Integer dailyTokenLimit;

    private List<String> allowedTools;

    private List<String> allowedSubflows;

    private Boolean enabled;
}
