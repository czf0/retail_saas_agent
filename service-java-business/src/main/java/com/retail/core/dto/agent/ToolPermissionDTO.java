package com.retail.core.dto.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具权限信息 DTO.
 * <p>由 Java 工具发现接口 (/api/v1/agent/tools/allowed) 返回给 Python,
 * 供 Python tool_registry 做工具级 L1 软拒绝 (查角色可用工具白名单).
 * <p>字段与 Python BaseTool.required_permission / sensitive_fields 对齐.
 */
@Data
public class ToolPermissionDTO {

    /** 工具名, 与 Python BaseTool.name 对齐 (如 order_query / inventory_check) */
    private String toolName;

    /** 所需权限标识, 与 Python BaseTool.required_permission 对齐 (如 business:order:query) */
    private String permission;

    /**
     * 敏感字段声明 (P2 字段级脱敏用), 列出该工具返回中可能被 Java 按角色脱敏的字段名.
     * 供 Python LLM 提示词适配, 避免 LLM 把脱敏后的"较高/中等"当成精确数值.
     */
    private List<String> sensitiveFields;
}
