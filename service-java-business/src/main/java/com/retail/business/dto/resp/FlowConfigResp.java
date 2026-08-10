package com.retail.business.dto.resp;

import lombok.Data;

import java.util.Map;

/**
 * Agent 工作流节点配置详情响应;聚合某 flow 下单个节点的参数 JSON(动态表单项渲染 + 后端执行时入参模板).
 * <p>Controller: GET /api/v1/agent/flows/{flowId}/nodes/{nodeId};参数 params JSON Schema 由前端 Flow Designer 定义.
 */
@Data
public class FlowConfigResp {

    private Long id;

    /** 所属工作流 name(唯一键;如"订单处理流程"). */
    private String flowName;

    /** 节点 name(flow 内唯一;如"查库存");前端 Flow 画布节点标题. */
    private String nodeName;

    /** 节点执行参数(动态 JSON,不同工具/节点类型结构不同;前端按 schema 渲染表单编辑). */
    private Map<String, Object> params;

    /** true = 该节点启用参与执行;false = 跳过(等效于画布中被禁用虚线节点). */
    private Boolean enabled;
}
