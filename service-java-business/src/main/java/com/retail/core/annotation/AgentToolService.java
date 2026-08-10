package com.retail.core.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具服务级注解 (按业务域聚合工具方法).
 * <p>
 * 标注在 Service 类上, 声明该类为 Agent 工具服务, 内部通过 {@link AgentTool} 方法级注解
 * 暴露具体工具. Spring 启动时由 {@code AgentToolRegistry} 自动扫描注册.
 * <p>
 * 设计目标 (避免类膨胀):
 * <ul>
 *   <li>同一业务域的工具方法聚合在一个 Service 类中 (如 StockAgentToolService 含 adjust + check);</li>
 *   <li>工具方法复用现有业务 Service (如 StockService), 不重写业务逻辑;</li>
 *   <li>方法级注解避免每个工具一个类, 减少样板代码.</li>
 * </ul>
 * <p>
 * 权限复用 SaToken: {@code requiredPermission} 默认推导为 {@code business:{business}:{operation}},
 * 与现有 Controller 的 {@code @SaCheckPermission} 值对齐, 直接复用 RBAC 体系.
 * <p>
 * {@code @Component} 使 Spring 自动扫描注册为 Bean, 供 AgentToolRegistry 发现.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AgentToolService {

    /**
     * 业务域标识 (如 "stock", "order", "stats").
     * <p>与 {@link AgentTool#operation()} 组合为 toolName: {@code business:operation} (如 "stock:adjust").
     */
    String business();
}
