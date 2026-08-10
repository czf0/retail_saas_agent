package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具定义注册表实体, 对应数据库 agent_tool_definition 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件); tenant_id=NULL 表示平台全局工具(所有租户共享), Service 层手动追加 OR tenant_id IS NULL 查询.
 * <p>业务约束: 工具元数据 + 权限的权威数据源 SSOT 在 Java(与 RBAC 同源); Python 启动时拉 /api/v1/agent/tools/registry 校验本地声明一致性, RoleContextNode 拉 /api/v1/agent/tools/allowed 做工具级软拒绝; JSON 列 inputSchema/outputSchema/annotations 映射为 String, Python 解析 dict, Java 仅存储透传不解析.
 * <p>唯一约束: UNIQUE(tenant_id, tool_name), 同范围下工具名不可重复(全局工具 tenant_id=NULL 与租户工具同名时, 租户工具优先覆盖).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("agent_tool_definition")
public class AgentToolDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具名(与 Python BaseTool.name 严格对齐, 如 order_query / inventory_adjust); UNIQUE(tenant_id, tool_name), 同名时租户工具优先覆盖全局工具. */
    private String toolName;

    /** 工具功能描述(供 LLM function calling 理解工具用途, 建议包含: 功能 + 适用场景 + 输入示例 + 返回示例); 描述质量直接影响 Agent 选工具准确率, 需精心编写. */
    private String description;

    /** 输入参数 Schema(JSON 字符串, 对齐 MCP inputSchema, 由 Python Pydantic args_schema 自动生成上传); Java 仅存储透传不解析, 空串 = 无参数工具. */
    private String inputSchema;

    /** 输出结果 Schema(JSON 字符串, 对齐 MCP outputSchema, 由 Python Pydantic output_schema 自动生成上传); Java 仅存储透传不解析, 空串 = 返回非结构化文本. */
    private String outputSchema;

    /** 所需 RBAC 权限标识(与 @SaCheckPermission 注解值严格匹配, 空串 = 无权限要求); RoleContextNode 拉 allowed 工具时按当前用户角色交集过滤, 无权限工具软拒绝(Agent 用话术引导, 不报错). */
    private String requiredPermission;

    /** 工具行为注解(JSON 字符串, 对齐 MCP hints: readOnly 只读不写 / destructive 破坏性写 / idempotent 幂等可重试 / openWorld 外部联网); 高风险工具(写操作)需二次确认后调用. */
    private String annotations;

    /** 工具分组标签(枚举字符串: java Java 内建工具 / db 数据库工具 / custom 自定义工具 / business 业务工具); 前端工具管理页按分组 Tab 展示, 便于分类检索. */
    private String toolGroup;

    /** 启停开关(1=ENABLED 启用, 0=DISABLED 禁用); 禁用后工具从 allowed 列表移除, Agent 完全看不到此工具(用于线上紧急下线有 Bug 的工具). */
    private Integer enabled = 1;

    /** 工具版本号(语义化版本如 1.2.0); Python 端校验与本地声明版本不一致时告警并拒绝加载(避免 Java/Python 两侧工具定义不一致引发事故). */
    private String version;

    /** 租户 id(TenantInterceptor 自动注入); NULL=平台全局工具, 所有租户共享; 非空=本租户自定义工具, 其他租户不可见; UNIQUE 组合索引包含此字段. */
    private Long tenantId;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateAt;
        private Integer deleted = 0;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
