"""
tool/base/tool_registry.py
全局工具注册、调度中心。
提供 @register_tool 装饰器自动注册工具，无需手动注册；
统一调度入口内置超时、重试、熔断容错切面。
"""
import asyncio
import time
from typing import Dict, List, Optional

from pydantic import ValidationError

from config.agent_flow_settings import agent_flow_settings
from core.exception import ErrorCode, ToolException, get_user_message
from core.logger import get_logger
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer
from schema.tool_schema import ToolMeta, ToolOutput

logger = get_logger("tool_registry")


class _CircuitBreaker:
    """简易熔断器：连续失败达阈值后熔断，恢复时间后半开探测。"""

    def __init__(self, threshold: int, recover_seconds: int):
        self.threshold = threshold
        self.recover_seconds = recover_seconds
        self.fail_count = 0
        self.opened_at = 0.0
        self.state = "closed"  # closed / open / half_open

    def allow(self) -> bool:
        if self.state == "closed":
            return True
        if self.state == "open":
            if time.time() - self.opened_at >= self.recover_seconds:
                self.state = "half_open"
                return True
            return False
        # half_open 仅放行一次探测
        return True

    def record_success(self) -> None:
        self.fail_count = 0
        self.state = "closed"

    def record_failure(self) -> None:
        self.fail_count += 1
        if self.state == "half_open" or self.fail_count >= self.threshold:
            self.state = "open"
            self.opened_at = time.time()


class ToolRegistry:
    """全局工具注册与调度中心。"""

    def __init__(self):
        self._tools: Dict[str, "BaseTool"] = {}
        self._breakers: Dict[str, _CircuitBreaker] = {}
        # L1 工具级软拒绝白名单: 由 RoleContextNode 从 Java allowedTools 接口拉取后写入.
        # 为空集合时表示白名单未加载 (初始状态), 不做软拒绝 (向后兼容);
        # 非空时仅放行白名单内的工具, 其余返回 PERMISSION_DENIED.
        self._allowed_tools: set = set()
        # 全局禁用集合: 由 tool_registry_sync 从 Java registry (enabled=0) 拉取后写入.
        # 优先级高于角色白名单 — 全局禁用的工具任何角色都不可调用,
        # 用于 Java SSOT 远程禁用工具时即时生效 (无需重启 Python).
        self._disabled_tools: set = set()

    # ---- L1 软拒绝白名单 ----
    def set_allowed_tools(self, tool_names: set) -> None:
        """设置当前角色可用的工具白名单 (从 Java allowedTools 接口拉取)."""
        self._allowed_tools = set(tool_names) if tool_names else set()
        logger.info(f"工具白名单已更新 count={len(self._allowed_tools)} tools={self._allowed_tools}")

    def get_allowed_tools(self) -> set:
        """获取当前工具白名单 (供审计/调试用)."""
        return set(self._allowed_tools)

    # ---- 全局禁用集合 (Java SSOT enabled=0) ----
    def set_disabled_tools(self, tool_names: set) -> None:
        """设置全局禁用工具集合 (从 Java registry enabled=0 拉取).

        全局禁用优先于角色白名单: 禁用集合内的工具任何角色都不可调用,
        用于 Java SSOT 远程下线工具时即时生效, 无需重启 Python.
        """
        self._disabled_tools = set(tool_names) if tool_names else set()
        if self._disabled_tools:
            logger.warning(f"工具全局禁用已更新 count={len(self._disabled_tools)} tools={self._disabled_tools}")

    def get_disabled_tools(self) -> set:
        """获取全局禁用工具集合 (供审计/调试用)."""
        return set(self._disabled_tools)

    # ---- 注册 ----
    def register(self, tool: "BaseTool") -> "BaseTool":
        """注册工具实例。"""
        self._tools[tool.name] = tool
        self._breakers[tool.name] = _CircuitBreaker(
            agent_flow_settings.TOOL_CIRCUIT_THRESHOLD,
            agent_flow_settings.TOOL_CIRCUIT_RECOVER,
        )
        logger.info(f"工具已注册 name={tool.name} group={tool.group}")
        return tool

    def register_tool(self, tool_cls):
        """@register_tool 装饰器：实例化并自动注册。"""
        instance = tool_cls()
        self.register(instance)
        return tool_cls

    # ---- 查询 ----
    def get(self, name: str) -> Optional["BaseTool"]:
        return self._tools.get(name)

    def list_tools(self) -> List[ToolMeta]:
        return [t.meta() for t in self._tools.values()]

    def list_names(self) -> List[str]:
        return list(self._tools.keys())

    # ---- 统一调度（超时 + 重试 + 熔断）----
    async def execute(
        self,
        name: str,
        parameters: Optional[dict] = None,
        timeout: Optional[int] = None,
        retry: Optional[int] = None,
    ) -> ToolOutput:
        """统一调度入口，封装容错切面。

        阶段3适配: 优先走 Java 动态加载的工具 (Java SSOT), 未命中则回退原生 Python 工具.
        - Java 工具: dynamic_java_tool_loader.get_definition 命中 → _execute_java_tool
          (HITL + 熔断/超时/重试 + Java /invoke, 权限校验由 Java @SaCheckPermission 兜底);
        - 原生工具: 回退现有逻辑 (向后兼容, 阶段5删除), 含白名单/禁用集/args_schema 校验.
        """
        parameters = parameters or {}

        # 阶段3: 优先走 Java 动态加载的工具 (Java SSOT)
        # 延迟 import 避免循环依赖 (tool.base.tool_registry ↔ tool.java.dynamic_java_tool_loader)
        try:
            from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
            java_defn = dynamic_java_tool_loader.get_definition(name)
        except ImportError:
            java_defn = None

        if java_defn is not None:
            return await self._execute_java_tool(name, java_defn, parameters, timeout, retry)

        # 回退: 原生 Python 工具 (向后兼容, 阶段5删除)
        tool = self._tools.get(name)
        if tool is None:
            # 评审 A1 修正: tag key 原 " name" 含前导空格, 修正为 "name"; 收集器统一改 otel_metrics
            otel_metrics.incr("tool_not_found", tags={"name": name})
            raise ToolException(f"工具不存在: {name}", code=ErrorCode.TOOL_NOT_FOUND)

        # 全局禁用检查 (Java SSOT enabled=0): 优先级高于角色白名单,
        # 全局禁用的工具任何角色都不可调用, 用于 Java 远程下线工具即时生效.
        if name in self._disabled_tools:
            otel_metrics.incr("tool_globally_disabled", tags={"name": name})
            logger.warning(f"工具已被全局禁用 name={name} (Java SSOT enabled=0)")
            return ToolOutput(
                success=False,
                error=get_user_message(ErrorCode.TOOL_DISABLED),
                error_code=ErrorCode.TOOL_DISABLED,
                tool_name=name,
            )

        # L1 工具级软拒绝: 白名单非空时, 仅放行白名单内的工具.
        # 白名单为空时 (初始状态/Java 不可用降级) 不做软拒绝, 由 Java RBAC 兜底.
        if self._allowed_tools and name not in self._allowed_tools:
            otel_metrics.incr("tool_permission_denied", tags={"name": name})
            logger.warning(f"工具权限不足 name={name} (不在角色白名单)")
            return ToolOutput(
                success=False,
                error=get_user_message(ErrorCode.TOOL_PERMISSION_DENIED),
                error_code=ErrorCode.TOOL_PERMISSION_DENIED,
                tool_name=name,
            )

        # Layer 1 输入强校验: 工具声明了 args_schema (Pydantic 模型) 时, 前置校验 LLM 传入参数.
        # 校验失败属确定性错误 (非瞬时故障), 不重试、不触发熔断, 直接返回结构化 error 喂回 LLM 自纠正.
        if tool.args_schema is not None:
            try:
                validated = tool.args_schema(**parameters)
                parameters = validated.model_dump(exclude_none=True)
            except ValidationError as ve:
                otel_metrics.incr("tool_input_invalid", tags={"name": name})
                logger.warning(f"工具参数校验失败 name={name} err={ve.errors()}")
                return ToolOutput(
                    success=False,
                    error=get_user_message(ErrorCode.TOOL_PARAM_INVALID),
                    error_code=ErrorCode.TOOL_PARAM_INVALID,
                    tool_name=name,
                )

        breaker = self._breakers[name]
        if not breaker.allow():
            otel_metrics.incr("tool_circuit_open", tags={"name": name})
            raise ToolException(
                get_user_message(ErrorCode.TOOL_CIRCUIT_OPEN),
                code=ErrorCode.TOOL_CIRCUIT_OPEN,
            )

        timeout_s = timeout or agent_flow_settings.TOOL_TIMEOUT
        max_retry = retry if retry is not None else agent_flow_settings.TOOL_MAX_RETRY
        last_output: Optional[ToolOutput] = None

        with otel_tracer.span(f"tool:{name}"):
            otel_metrics.incr("tool_call_total", tags={"name": name})
            for attempt in range(max_retry + 1):
                try:
                    output = await asyncio.wait_for(tool.run(parameters), timeout=timeout_s)
                    if output.success:
                        breaker.record_success()
                        otel_metrics.incr("tool_call_success", tags={"name": name})
                        otel_metrics.observe("tool_cost_ms", output.cost_ms, tags={"name": name})
                        return output
                    # 工具内部失败，记录但继续重试
                    last_output = output
                    logger.warning(
                        f"工具执行失败 name={name} attempt={attempt} err={output.error}"
                    )
                except asyncio.TimeoutError:
                    last_output = ToolOutput(
                        success=False,
                        error=get_user_message(ErrorCode.TOOL_TIMEOUT),
                        error_code=ErrorCode.TOOL_TIMEOUT,
                        tool_name=name,
                    )
                    logger.warning(f"工具执行超时 name={name} attempt={attempt} timeout={timeout_s}s")
                except Exception as exc:
                    last_output = ToolOutput(
                        success=False,
                        error=get_user_message(ErrorCode.TOOL_EXEC_ERROR),
                        error_code=ErrorCode.TOOL_EXEC_ERROR,
                        tool_name=name,
                    )
                    logger.warning(f"工具执行异常 name={name} attempt={attempt} err={exc}")

            # 全部重试失败
            breaker.record_failure()
            otel_metrics.incr("tool_call_failed", tags={"name": name})
            return last_output or ToolOutput(
                success=False,
                error=get_user_message(ErrorCode.TOOL_EXEC_ERROR),
                error_code=ErrorCode.TOOL_EXEC_ERROR,
                tool_name=name,
            )

    # ---- Java 工具执行 (阶段3: 动态加载 Java @AgentTool) ----

    def _get_or_create_breaker(self, name: str) -> "_CircuitBreaker":
        """获取或创建工具熔断器 (Java 工具未走 register, 按需创建).

        Java 工具不在 self._tools 中注册 (动态加载), 其熔断器需在首次调用时创建.
        复用与原生工具相同的熔断参数 (TOOL_CIRCUIT_THRESHOLD / TOOL_CIRCUIT_RECOVER).
        """
        if name not in self._breakers:
            self._breakers[name] = _CircuitBreaker(
                agent_flow_settings.TOOL_CIRCUIT_THRESHOLD,
                agent_flow_settings.TOOL_CIRCUIT_RECOVER,
            )
        return self._breakers[name]

    async def _execute_java_tool(
        self,
        name: str,
        defn: "JavaToolDefinition",
        parameters: dict,
        timeout: Optional[int] = None,
        retry: Optional[int] = None,
    ) -> ToolOutput:
        """Java @AgentTool 统一执行: HITL + 熔断/超时/重试 + Java /invoke.

        与原生 execute() 的区别:
        - HITL 下沉: destructive=True 时在重试循环外调 interrupt() (不重复中断);
          Skill 路径无 graph 上下文时 interrupt() 抛异常, 安全降级为 error (不执行破坏性操作);
        - 权限校验: 由 Java /invoke 内 @SaCheckPermission 兜底, Python 侧不做白名单/禁用集检查;
        - args_schema 校验: 由 LangChain StructuredTool 层 (Pydantic) 完成, 此处不重复;
        - 输出格式化: Java 返回原始业务对象, formatted_content=None (LLM 据 outputHint 组织, 阶段4注入).

        确定性错误 (PERMISSION_DENIED / TOOL_DISABLED / TOOL_NOT_FOUND / PARAM_INVALID) 不重试:
        这些错误重试也不会成功, 直接返回喂回 LLM 自纠正.
        """
        # 延迟 import 避免循环依赖
        from tool.java.java_invoke_client import java_invoke_client

        # 1. HITL 检查 (destructive → interrupt, 在重试循环外 — 同一审批不重复中断)
        if defn.destructive:
            # 延迟 import: interrupt() 函数 + GraphInterrupt 异常类
            # GraphInterrupt 必须在 except 中显式 re-raise, 让 LangGraph runtime 捕获并暂停 graph;
            # 若被 except Exception 吞掉, graph 无法暂停, HITL 机制失效.
            from langgraph.errors import GraphInterrupt
            from langgraph.types import interrupt

            try:
                approval_request = {
                    "tool": name,
                    "args": parameters,
                    "description": defn.description,
                }
                logger.info(f"hitl_interrupt tool={name} args={parameters}")
                # interrupt() 暂停 graph 执行, 状态持久化到 checkpointer;
                # resume 请求通过 Command(resume=decision) 恢复, interrupt() 返回 decision.
                decision = interrupt(approval_request)

                # 解析用户决策
                if not (isinstance(decision, dict) and decision.get("approved")):
                    reason = (
                        decision.get("reason", "用户未提供拒绝原因")
                        if isinstance(decision, dict)
                        else "用户未提供拒绝原因"
                    )
                    logger.info(f"hitl_rejected tool={name} reason={reason}")
                    return ToolOutput(
                        success=False,
                        error=get_user_message(ErrorCode.TOOL_HITL_REJECTED),
                        error_code=ErrorCode.TOOL_HITL_REJECTED,
                        tool_name=name,
                    )
                logger.info(f"hitl_approved tool={name}, 继续执行")
            except ToolException:
                # tool_registry 自身的 ToolException 直接抛出 (如熔断), 不在此捕获
                raise
            except GraphInterrupt:
                # LangGraph interrupt 机制: interrupt() 抛出 GraphInterrupt,
                # 必须传播到 graph runtime 让其暂停 graph 并持久化 checkpoint.
                # 不能在此捕获, 否则 graph 无法暂停, HITL 机制失效.
                raise
            except Exception as exc:
                # 非 graph 上下文中的其他异常 (如 Skill 路径无 checkpointer 时 interrupt 退化异常),
                # 安全降级为 error — 不执行破坏性操作 (保守策略, 避免未经审批的破坏性调用).
                logger.warning(f"hitl_no_graph_context tool={name} err={exc}")
                otel_metrics.incr("hitl_no_graph_context", tags={"name": name})
                return ToolOutput(
                    success=False,
                    error=get_user_message(ErrorCode.TOOL_HITL_NO_CONTEXT),
                    error_code=ErrorCode.TOOL_HITL_NO_CONTEXT,
                    tool_name=name,
                )

        # 2. 熔断器检查 (Java 工具按需创建 breaker)
        breaker = self._get_or_create_breaker(name)
        if not breaker.allow():
            otel_metrics.incr("tool_circuit_open", tags={"name": name})
            raise ToolException(
                get_user_message(ErrorCode.TOOL_CIRCUIT_OPEN),
                code=ErrorCode.TOOL_CIRCUIT_OPEN,
            )

        # 3. 超时/重试循环 (网络抖动 → 重试, 幂等性由 Java Redis 兜底)
        timeout_s = timeout or agent_flow_settings.TOOL_TIMEOUT
        max_retry = retry if retry is not None else agent_flow_settings.TOOL_MAX_RETRY
        last_output: Optional[ToolOutput] = None

        # 确定性错误码集合 (Integer): 这些错误重试也不会成功, 直接返回喂 LLM 自纠正
        _DETERMINISTIC_ERRORS = frozenset({
            ErrorCode.TOOL_PERMISSION_DENIED, ErrorCode.TOOL_DISABLED, ErrorCode.TOOL_NOT_FOUND,
            ErrorCode.TOOL_PARAM_INVALID, ErrorCode.TOOL_HITL_REJECTED, ErrorCode.TOOL_HITL_NO_CONTEXT,
        })

        with otel_tracer.span(f"tool:{name}"):
            otel_metrics.incr("tool_call_total", tags={"name": name, "source": "java"})
            for attempt in range(max_retry + 1):
                try:
                    result = await asyncio.wait_for(
                        java_invoke_client.invoke(
                            defn.business, defn.operation, parameters,
                        ),
                        timeout=timeout_s,
                    )

                    # Java 业务成功
                    if result.get("success"):
                        breaker.record_success()
                        otel_metrics.incr("tool_call_success", tags={"name": name, "source": "java"})
                        otel_metrics.observe(
                            "tool_cost_ms", result.get("elapsedMs", 0), tags={"name": name},
                        )
                        return ToolOutput(
                            success=True,
                            data=result.get("data"),
                            # Java 不做格式化, LLM 据 outputHint 组织输出 (阶段4注入 system prompt)
                            formatted_content=None,
                            cost_ms=result.get("elapsedMs", 0),
                            tool_name=name,
                        )

                    # Java 业务错误 (success=false) — errorCode 为 Integer (与 Java ErrCodeEnum 对齐)
                    error_code = result.get("errorCode") or ErrorCode.TOOL_EXEC_ERROR
                    error_msg = result.get("error") or get_user_message(ErrorCode.TOOL_EXEC_ERROR)
                    otel_metrics.incr("tool_call_failed", tags={"name": name, "code": str(error_code)})

                    # 确定性错误 (权限/禁用/不存在/参数错误) 不重试, 直接返回
                    if error_code in _DETERMINISTIC_ERRORS:
                        breaker.record_success()  # 业务错误非瞬时故障, 不计入熔断
                        return ToolOutput(
                            success=False,
                            error=error_msg,
                            error_code=error_code,
                            tool_name=name,
                        )

                    # 非确定性错误 (EXECUTION_ERROR 等) 记录并继续重试
                    last_output = ToolOutput(
                        success=False,
                        error=error_msg,
                        error_code=error_code,
                        tool_name=name,
                    )
                    logger.warning(
                        f"java_tool_failed name={name} attempt={attempt} code={error_code} err={error_msg}"
                    )

                except asyncio.TimeoutError:
                    last_output = ToolOutput(
                        success=False,
                        error=get_user_message(ErrorCode.TOOL_TIMEOUT),
                        error_code=ErrorCode.TOOL_TIMEOUT,
                        tool_name=name,
                    )
                    logger.warning(f"java_tool_timeout name={name} attempt={attempt} timeout={timeout_s}s")
                except Exception as exc:
                    last_output = ToolOutput(
                        success=False,
                        error=get_user_message(ErrorCode.TOOL_EXEC_ERROR),
                        error_code=ErrorCode.TOOL_EXEC_ERROR,
                        tool_name=name,
                    )
                    logger.warning(f"java_tool_exception name={name} attempt={attempt} err={exc}")

            # 全部重试失败
            breaker.record_failure()
            otel_metrics.incr("tool_call_failed", tags={"name": name, "source": "java"})
            return last_output or ToolOutput(
                success=False,
                error=get_user_message(ErrorCode.TOOL_EXEC_ERROR),
                error_code=ErrorCode.TOOL_EXEC_ERROR,
                tool_name=name,
            )


# 全局工具注册中心单例
tool_registry = ToolRegistry()


def register_tool(tool_cls):
    """模块级 @register_tool 装饰器。"""
    return tool_registry.register_tool(tool_cls)
