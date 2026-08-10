package com.retail.business.dto.resp;

import lombok.Data;

/**
 * AI Agent 可调用工具注册表项列表/详情响应(Python sidecar 拉取 /registry 对齐 MCP tools/list + 前端"Agent 工具管理"页共用);暴露工具名/描述/入参 JSON Schema/权限要求/HITL 破坏性标记/输出约束/分组/启停.
 * <p>Controller: GET /api/v1/agent/tools;Python sidecar 启动时拉一次 + 每 30min 增量刷新.
 */
@Data
public class AgentToolDefinitionResp {

    /** 工具唯一标识(对齐 @AgentTool toolName,格式 business:operation;Python 侧按此名称反射调用 Java 方法). */
    private String toolName;

    /** 工具自然语言描述(对齐 @AgentTool.description,含 2+ 触发词;LLM 意图路由 + 前端工具详情页展示用). */
    private String description;

    /** 入参 JSON Schema(JSON 字符串,对齐 MCP inputSchema;前端工具配置表单 + LLM 参数生成双用;Java 端只做字符串透传不解析). */
    private String inputSchema;

    /** 调用所需权限标识(格式 business:module:action;对齐 @SaCheckPermission;空串 = 无权限要求,访客工具). */
    private String requiredPermission;

    /** 破坏性操作 HITL 标记(true = Python 侧调用前触发人工二次确认 interrupt;如下架/删除/调价). */
    private Boolean destructive;

    /** 输出格式约束提示(注入 Python ReAct system prompt;如"仅返回 JSON 数组/限制 200 字内",约束 LLM 输出形态). */
    private String outputHint;

    /** 工具分组:java=Java 反射工具 / db=SQL 查询工具 / custom=自定义脚本 / business=业务域聚合工具;前端工具列表按组折叠. */
    private String toolGroup;

    /** 启停状态:1=启用可被调用 0=禁用从注册表摘除;Python 侧拉不到 enabled=0 的工具(前端灰显). */
    private Integer enabled;
}
