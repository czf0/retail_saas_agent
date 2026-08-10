"""
other_agent/prompt/base.py
Prompt 提供者抽象与全局注册表.

# ============= DEPRECATED =====================================
# 本模块已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
# 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 unified_agent/prompt.py.
# ==============================================================

设计说明:
- 对齐项目既有注册表风格 (tool_registry / NodeRegistry): 单例 + ABC + register/set 模式;
- DefaultPromptProvider 内容即现有 5 处散落常量原文抽取, 保证零行为变化 (向后兼容);
- 业务上下文 (business_context) 单独建模为可组合片段, 由 flow 在拼装 system prompt 时叠加,
  避免把业务知识写死在范式 prompt 里 (范式 prompt 描述"怎么做", 业务上下文描述"做什么");
- get_provider(ctx_or_state) 优先读 ctx.meta["prompt_provider"] / state["prompt_provider"]
  (per-request 隔离), 回退 registry 单例 — 解决 LayeredOrchestrator (零售) 与
  LCOrchestrator (通用) 共存时的 provider 隔离问题, 避免单例污染.

解决的问题:
- Prompt 硬编码散落 5 处, 无法按业务切换;
- ReAct create_react_agent(prompt=) 与 WorkFlow _DEFAULT_NODES 早期绑定, provider 切换不生效
  (由各 flow 改造为运行期取 provider 解决, 见 flow 改造);
- RAG 注入格式不统一 (三处 flow 各自拼装), 统一走 rag_wrap.
"""
# ============= DEPRECATED =====================================
# 本模块已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
# 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 unified_agent/prompt.py.
# ==============================================================
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional, Tuple

from other_agent.core.types import FlowContext


class PromptProvider(ABC):
    """Prompt 提供者抽象.

    # ============= DEPRECATED =====================================
    # 本类已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
    # 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 unified_agent/prompt.py.
    # ==============================================================

    子类覆写 8 个方法, 提供具体 prompt 文本.
    方法分两类:
    - 范式 prompt (react_system / workflow_nodes / plan_system / summary_system): 描述"怎么做";
    - 辅助 prompt (classifier_system / judge_system): 路由与反思用;
    - 可组合片段 (business_context / rag_wrap): 由 flow 在拼装时叠加, business_context 按角色分化.
    """

    @abstractmethod
    def react_system(self) -> str:
        """ReAct 系统提示 (基础身份 + 工具/数据/权限规范, 无业务上下文).

        业务上下文由 flow 在拼装时叠加 business_context(role), 不在此方法内硬编码.
        """

    @abstractmethod
    def workflow_nodes(self) -> List[Tuple[str, str]]:
        """WorkFlow 节点模板列表: [(node_name, prompt_template), ...].

        模板支持 {input} / {context} 占位符, {context} 由 flow 用 rag_wrap 结果替换.
        """

    @abstractmethod
    def plan_system(self, max_subtasks: int) -> str:
        """Plan & Execute 规划提示, max_subtasks 渲染到模板."""

    @abstractmethod
    def summary_system(self) -> str:
        """Plan & Execute 汇总提示."""

    @abstractmethod
    def classifier_system(self) -> str:
        """范式路由分类器系统提示 (输出 react/plan_execute/workflow 三者之一)."""

    @abstractmethod
    def judge_system(self) -> str:
        """答案质量评判系统提示 (输出 ok/degraded)."""

    @abstractmethod
    def business_context(self, role: str = "") -> str:
        """业务上下文片段, 按角色分化. 由 flow 在拼装 system prompt 时叠加.

        通用实现返回空串 (flow 拼装时自然跳过); 业务实现返回角色相关的业务知识.
        """

    @abstractmethod
    def rag_wrap(self, context_text: str) -> str:
        """RAG 上下文包装格式. 空串入参返回空串 (供 flow 判空跳过注入).

        统一三处 flow 的 RAG 注入格式, 替代各自内联拼装.
        """


class DefaultPromptProvider(PromptProvider):
    """通用默认实现.

    # ============= DEPRECATED =====================================
    # 本类已标记遗留, 2026-08 之后统一以 unified_agent/prompt.py 为权威 Prompt 源.
    # 仅保留通用回退, 不得新增业务 Prompt 文本; 新 Prompt 统一写到 UnifiedPromptProvider.
    # ==============================================================

    内容即现有 5 处散落常量原文抽取, 保证零行为变化 (向后兼容).
    LCOrchestrator (面试/通用) 走此 provider; 测试回归用此 provider 对照.
    """

    def react_system(self) -> str:
        # 原文抽自 other_agent/flow/react_flow.py 的 _REACT_SYSTEM
        return (
            "你是一个严格遵循 ReAct 范式的通用助手。遇到需要工具的问题时调用工具，"
            "得到充足信息后给出最终回答。回答需简洁准确。\n"
            "权限约束: 如果工具返回 PERMISSION_DENIED 或\"权限不足\", 说明当前用户无权调用该工具. "
            "此时不要重试该工具, 基于已有数据回答, 并告知用户该部分数据无权限查看."
        )

    def workflow_nodes(self) -> List[Tuple[str, str]]:
        # 原文抽自 other_agent/flow/workflow_flow.py 的 _DEFAULT_NODES
        return [
            ("understand", "请理解并复述用户意图：{input}"),
            ("respond", "基于以下信息给出回答：{input}"),
        ]

    def plan_system(self, max_subtasks: int) -> str:
        # 原文抽自 other_agent/flow/plan_exec_flow.py 的 _PLAN_SYSTEM (含 {max_subtasks} 渲染)
        return (
            "你是一个任务规划器。请将用户请求拆分为不超过 {max_subtasks} 个有序子任务，"
            "以 JSON 数组输出，每个元素包含字段：id（序号）、task（子任务描述）。"
            "仅输出 JSON，不要多余解释。"
        ).replace("{max_subtasks}", str(max_subtasks))

    def summary_system(self) -> str:
        # 原文抽自 other_agent/flow/plan_exec_flow.py 的 _SUMMARY_SYSTEM
        return "你是一个汇总器。请根据用户原始请求与各子任务结果，给出整合后的最终回答。"

    def classifier_system(self) -> str:
        # 原文抽自 flow_architecture/paradigm_router.py 的 _CLASSIFIER_SYSTEM
        return (
            "你是范式路由器. 根据用户问题判断应使用哪种执行范式, 只输出一个单词:\n"
            "- react: 需要工具调用与多步推理的问题\n"
            "- plan_execute: 复杂多步任务, 需拆解为子任务并行执行\n"
            "- workflow: 线性流程化处理 (理解 -> 回答)\n"
            "仅输出 react / plan_execute / workflow 三者之一, 不要任何解释."
        )

    def judge_system(self) -> str:
        # 原文抽自 flow_architecture/reflect.py 的 LLMReflector._JUDGE_SYSTEM
        return (
            "你是答案质量评判器. 根据以下维度评判答案质量:\n"
            "1. 是否回应了用户问题;\n"
            "2. 是否基于工具返回的数据 (而非编造).\n"
            "只输出一个词: ok (合格) 或 degraded (不合格). 不要解释."
        )

    def business_context(self, role: str = "") -> str:
        # 通用版无业务上下文, 返回空串 (flow 拼装时自然跳过)
        return ""

    def rag_wrap(self, context_text: str) -> str:
        # 通用版对齐现有 ReAct 行为 (其他 flow 用 {context} 占位符走同一格式)
        if not context_text:
            return ""
        return f"参考以下上下文回答问题：\n{context_text}"


class PromptRegistry:
    """Prompt 提供者注册表单例. 对齐 tool_registry 风格.

    生产路径: LayeredOrchestrator 通过 ctx.meta 透传 provider, 不调 set_provider;
    set_provider 仅用于测试/灰度逃逸口 (全局切换 provider).
    """

    def __init__(self) -> None:
        self._provider: PromptProvider = DefaultPromptProvider()

    def set_provider(self, provider: PromptProvider) -> None:
        """切换全局默认 provider. 影响所有未在 ctx.meta 显式指定 provider 的 flow.

        警告: 进程级单例, 会影响所有 orchestrator. 生产路径应通过 ctx.meta 透传,
        此方法仅用于单元测试/灰度全量切换.
        """
        self._provider = provider

    def get_provider(self) -> PromptProvider:
        return self._provider


# 全局单例 (与 tool_registry 同级, 模块导入即就绪)
prompt_registry = PromptRegistry()


def get_provider(ctx_or_state) -> PromptProvider:
    """统一取 provider 入口: 优先 ctx.meta["prompt_provider"] / state["prompt_provider"],
    回退 prompt_registry 全局单例.

    存在该 helper 的原因: LayeredOrchestrator 独立持有 RetailPromptProvider 实例并写入 ctx.meta,
    实现"LCOrchestrator 用 Default, LayeredOrchestrator 用 Retail"的 per-request 隔离,
    避免单例污染 (LCOrchestrator 面试用, 必须保持通用 prompt).

    Args:
        ctx_or_state: FlowContext (flow 内) 或 dict (PreflightState / _PlanExecState 等图状态).

    Returns:
        PromptProvider 实例 (ctx.meta/state 指定优先, 否则 registry 单例).
    """
    override: Optional[PromptProvider] = None
    if isinstance(ctx_or_state, FlowContext):
        override = (ctx_or_state.meta or {}).get("prompt_provider")
    elif isinstance(ctx_or_state, dict):
        override = ctx_or_state.get("prompt_provider")
    return override or prompt_registry.get_provider()
