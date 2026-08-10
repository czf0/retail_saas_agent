package com.retail.core.dto.agent;

import lombok.Data;

import java.lang.reflect.Method;

/**
 * Agent 工具元数据 (AgentToolRegistry 扫描 @AgentTool 方法后构建).
 * <p>
 * 持有工具的完整运行时信息: 注解元数据 + Service Bean + Method 引用 + 输入类型,
 * 供 {@code AgentToolInvokeController.invoke} 反射调用.
 * <p>
 * toolName 格式: {@code business:operation} (如 "stock:adjust"), 二级定位使用.
 * <p>
 * enabled 状态: 从 DB {@code agent_tool_definition.enabled} 读取, 支持运行时禁用工具
 * (管理界面修改 enabled=0 → invoke 前拒绝执行, 解决两端工具列表不一致问题).
 *
 * @see com.retail.core.annotation.AgentToolService
 * @see com.retail.core.annotation.AgentTool
 */
@Data
public class ToolMeta {

    /** 工具名 (business:operation, 如 "stock:adjust") */
    private String toolName;

    /** 业务域 (如 "stock") */
    private String business;

    /** 操作标识 (如 "adjust") */
    private String operation;

    /** 工具描述 (喂 LLM) */
    private String description;

    /** 权限标识 (默认推导: business:{business}:{operation}, 对齐 @SaCheckPermission) */
    private String requiredPermission;

    /** 是否破坏性操作 (HITL 标记) */
    private boolean destructive;

    /** 输出格式提示 (注入 Python system prompt) */
    private String outputHint;

    /** 输入 JSON Schema (反射方法参数类型生成, 供 Python 构建 Pydantic args_schema) */
    private String inputSchema;

    /** @AgentToolService Bean 实例 (反射调用时作为 method.invoke 的 receiver) */
    private Object serviceBean;

    /** @AgentTool 方法引用 (反射调用) */
    private Method method;

    /** 方法参数类型 (反序列化 JSON args 时使用) */
    private Class<?> inputType;

    /** 是否启用 (从 DB agent_tool_definition.enabled 读取, 运行时可修改) */
    private boolean enabled;
}
