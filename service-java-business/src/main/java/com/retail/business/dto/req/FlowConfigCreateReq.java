package com.retail.business.dto.req;

import lombok.Data;

import java.util.Map;

/**
 * 流程配置创建请求, 运营后台工作流管理 -> 新增流程节点, 配置节点名/参数并默认启用.
 * <p>对应 Controller 路由: POST /api/v1/flow-config; enabled 默认 true, status 由 Service 层赋默认值(铁律 6).
 */
@Data
public class FlowConfigCreateReq {

    private String flowName;

    /** 节点名称,默认 "*" */
    private String nodeName = "*";

    private Map<String, Object> params;

    private Boolean enabled = true;
}
