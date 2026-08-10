package com.retail.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具方法级注解 (标注具体工具方法).
 * <p>
 * 标注在 {@link AgentToolService} 类的方法上, 声明该方法为 Agent 可调用的工具.
 * {@code AgentToolRegistry} 启动时扫描所有 {@code @AgentToolService} Bean, 遍历方法
 * 查找 {@code @AgentTool} 注解, 反射方法参数类型生成 JSON Schema, 注册到内存 Map.
 * <p>
 * toolName 格式: {@code business:operation} (如 "stock:adjust"), 二级定位调用时使用.
 * <p>
 * 权限推导: {@code requiredPermission} 默认为 {@code business:{business}:{operation}},
 * 与现有 Controller 的 {@code @SaCheckPermission} 值对齐 (如 "business:stock:adjust"),
 * 复用 SaToken RBAC 体系, 无需额外维护权限映射表. 特殊情况可显式覆盖.
 * <p>
 * 破坏性操作: {@code destructive=true} 时, Python 端 {@code _make_invoke_coroutine}
 * 会注入 LangGraph {@code interrupt()} 暂停 graph, 等待用户审批后再 resume.
 * <p>
 * 输出格式提示: {@code outputHint} 为纯字符串, 注入 Python ReAct system prompt,
 * 约束 LLM 根据工具返回的原始数据自行组织输出格式 (Java 不做格式化).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /**
     * 操作标识 (如 "adjust", "query", "check").
     * <p>与 {@link AgentToolService#business()} 组合为 toolName: {@code business:operation}.
     */
    String operation();

    /** 工具描述 (喂 LLM, 供 function calling 理解工具用途) */
    String description();

    /**
     * 权限标识.
     * <p>默认值 {@code "<<derive>>"} 表示自动推导为 {@code business:{business}:{operation}},
     * 与现有 Controller 的 {@code @SaCheckPermission} 值对齐, 复用 SaToken RBAC.
     * <p>显式设为空串 {@code ""} 表示无权限要求 (依赖多租户隔离, 如 StatsController 无 @SaCheckPermission).
     * <p>也可显式指定其他权限标识 (如 {@code "system:config:query"}).
     */
    String requiredPermission() default "<<derive>>";

    /**
     * 是否破坏性操作 (HITL 标记).
     * <p>{@code true} 时 Python 端注入 {@code interrupt()} 暂停 graph, 等待用户审批.
     */
    boolean destructive() default false;

    /**
     * 输出格式提示 (纯字符串, 注入 Python ReAct system prompt).
     * <p>约束 LLM 根据工具返回的原始数据自行组织输出格式, Java 不做格式化.
     * 如: "返回库存列表, 展示为 markdown 表格, 金额保留 2 位小数".
     */
    String outputHint() default "";
}
