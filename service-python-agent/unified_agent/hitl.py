"""
unified_agent/hitl.py
Human-in-the-Loop (HITL) 工具包装器: 拦截破坏性工具调用, 通过 LangGraph interrupt()
同步阻塞等待用户审批, 审批通过后执行工具, 拒绝则返回拒绝文案供 LLM 调整方案.

设计原理:
- LangGraph interrupt() 是同步阻塞原语: 在 ToolNode 执行工具时调用, graph 暂停,
  状态持久化到 RedisSaver (checkpointer), astream_events 生成器结束;
- resume 请求携带用户决策 (approved/rejected + reason), 通过 Command(resume=decision)
  恢复 graph 执行, interrupt() 返回 decision, 据此决定是否执行工具;
- 状态跨 HTTP 请求续接: 原始 stream 请求 → pending_approval chunk → 连接关闭;
  resume 请求 → 新 SSE 连接 → 续接 graph → token/done chunk.

集成位置:
- graph.py _get_react_graph: load_langchain_tools() 后调 wrap_tools_with_hitl(),
  对 destructive_hint=True 的工具注入 interrupt() 包装, 非破坏性工具零开销透传.

解决的问题:
- 破坏性操作 (退款/库存调整/数据删除) 直接执行风险高 → 人工审批兜底;
- 审批卡片需工具名 + 参数 + 描述 → interrupt value 携带完整工具调用信息;
- 用户拒绝后 LLM 需感知拒绝原因 → 拒绝文案作为 ToolMessage 喂回 LLM 自纠正.
"""
from typing import Any, Dict, List

from langchain_core.tools import BaseTool as LCTool
from langchain_core.tools import StructuredTool
from langgraph.types import interrupt

from core.logger import get_logger
from tool.base.tool_registry import tool_registry

logger = get_logger("hitl")


def is_destructive_tool(tool_name: str) -> bool:
    """检查工具是否标记为破坏性 (annotations.destructive_hint=True).

    从原生 tool_registry 获取工具注解, 非破坏性工具 (默认 destructive_hint=False) 返回 False.
    工具不存在时返回 False (安全降级, 不阻断未知工具).
    """
    native = tool_registry.get(tool_name)
    if native is None:
        return False
    return native.annotations.destructive_hint


def _make_hitl_coroutine(original_coroutine, tool_name: str, tool_description: str):
    """为破坏性工具构造 HITL 包装协程: 执行前 interrupt() 等待用户审批.

    interrupt() 调用时 graph 暂停, 状态持久化到 RedisSaver.
    resume 时 interrupt() 返回用户决策 dict:
        {"approved": True}  → 执行原工具
        {"approved": False, "reason": "..."}  → 返回拒绝文案喂 LLM
    """

    async def _hitl_arun(**kwargs: Any) -> Any:
        # 构造审批请求信息 (供前端展示工具名/参数/描述)
        approval_request: Dict[str, Any] = {
            "tool": tool_name,
            "args": kwargs,
            "description": tool_description,
        }
        logger.info(f"hitl_interrupt tool={tool_name} args={kwargs}")
        # interrupt() 暂停 graph 执行, 状态持久化到 checkpointer (RedisSaver);
        # resume 请求通过 Command(resume=decision) 恢复, interrupt() 返回 decision.
        decision = interrupt(approval_request)

        # 解析用户决策
        if isinstance(decision, dict) and decision.get("approved"):
            logger.info(f"hitl_approved tool={tool_name}, 继续执行")
            return await original_coroutine(**kwargs)
        else:
            reason = ""
            if isinstance(decision, dict):
                reason = decision.get("reason", "用户未提供拒绝原因")
            else:
                reason = "用户未提供拒绝原因"
            logger.info(f"hitl_rejected tool={tool_name} reason={reason}")
            # 返回拒绝文案作为 ToolMessage content, 喂回 LLM 供其调整方案或提供替代建议
            return (
                f"用户已拒绝执行此操作 (工具: {tool_name}). "
                f"拒绝原因: {reason}. "
                f"请根据用户意愿调整方案, 或提供不涉及此操作的替代建议."
            )

    return _hitl_arun


def wrap_tool_with_hitl(lc_tool: LCTool) -> LCTool:
    """包装单个 LangChain StructuredTool: 破坏性工具注入 interrupt(), 非破坏性工具原样返回.

    非破坏性工具直接返回原对象 (零开销, 不创建新包装);
    破坏性工具创建新 StructuredTool, coroutine 替换为 HITL 包装版本, 其余属性 (name/description/args_schema) 保持一致.
    """
    tool_name = lc_tool.name
    if not is_destructive_tool(tool_name):
        return lc_tool

    # 获取原工具的 coroutine (StructuredTool.coroutine 属性)
    original_coroutine = getattr(lc_tool, "coroutine", None)
    if original_coroutine is None:
        # 同步工具无 coroutine, 无法包装 HITL (create_react_agent 异步执行路径不触发)
        logger.warning(f"hitl_skip tool={tool_name} 无异步 coroutine, 跳过 HITL 包装")
        return lc_tool

    description = lc_tool.description or f"工具 {tool_name}"
    hitl_coroutine = _make_hitl_coroutine(original_coroutine, tool_name, description)

    logger.info(f"hitl_wrap tool={tool_name} 已注入人工审批拦截")
    return StructuredTool(
        name=lc_tool.name,
        description=lc_tool.description,
        coroutine=hitl_coroutine,
        args_schema=lc_tool.args_schema,
    )


def wrap_tools_with_hitl(tools: List[LCTool]) -> List[LCTool]:
    """批量包装工具列表: 对破坏性工具注入 HITL interrupt().

    graph.py _get_react_graph 在 load_langchain_tools() 后调用此函数,
    确保所有 destructive_hint=True 的工具在执行前需用户审批.
    """
    wrapped: List[LCTool] = []
    hitl_count = 0
    for tool in tools:
        wrapped_tool = wrap_tool_with_hitl(tool)
        if wrapped_tool is not tool:
            hitl_count += 1
        wrapped.append(wrapped_tool)
    if hitl_count > 0:
        logger.info(f"hitl_tools_wrapped count={hitl_count} total={len(tools)}")
    return wrapped
