"""
unified_agent/obs/token_accumulator.py
Per-request LLM token 累加器 (P3: 填充 done.meta.tokens_used).

设计说明:
- unified_agent 用 create_react_agent (LangGraph prebuilt) 编排, LLM 调用在 agent 内部完成,
  usage 不经 graph 暴露; 通过 LangChain BaseCallbackHandler 的 on_llm_end 钩子捕获每次 LLM 调用
  的 token 使用量, 累加到 contextvars.ContextVar (per-request 隔离, 跨 asyncio Task 传播).
- graph 在 done chunk / orchestrator 在 FlowResult.meta 读取累计值, 填充 tokens_used,
  供 Java StreamChatHandler.onDone 持久化到 chat_message (当前始终 null 的根因修复).
- 对齐 unified_agent/llm.py 的 _extract_usage 逻辑 (usage_metadata 优先, 回退 token_usage).

为何不用 otel_metrics llm_tokens_total: 该指标是全局累加计数器 (跨请求共享), 无法区分单请求;
ContextVar 实现 per-request 隔离, 与请求生命周期对齐。
"""
from __future__ import annotations

import contextvars
from typing import Any

from langchain_core.callbacks import BaseCallbackHandler

from core.logger import get_logger

logger = get_logger("token_accumulator")

# Per-request token 累加器 ContextVar; 默认 0.
# graph 入口处 reset, 每次 on_llm_end 累加, done chunk / FlowResult.meta 读取.
_token_total: contextvars.ContextVar[int] = contextvars.ContextVar(
    "unified_llm_token_total", default=0
)


def reset_token_total() -> None:
    """重置当前请求的 token 累加器 (graph 执行入口调用, 确保单请求隔离)."""
    _token_total.set(0)


def get_token_total() -> int:
    """获取当前请求累计的 token 数 (done chunk / FlowResult.meta 读取)."""
    return _token_total.get()


def _extract_usage_from_response(response: Any) -> int:
    """从 LangChain on_llm_end 的 response 提取 total_tokens.

    兼容多种 response 形态 (不同 langchain 版本/模型回传结构不同):
    - AIMessage 直传: usage_metadata.total_tokens (新版 langchain);
    - LLMResult.llm_output: token_usage.total_tokens (OpenAI 原始字段);
    - LLMResult.generations[0][0].message: usage_metadata.total_tokens.
    返回 0 表示无可提取 usage (如本地 Ollama 部分版本不回传 usage).
    """
    # 1. AIMessage 直传 (usage_metadata)
    usage = getattr(response, "usage_metadata", None)
    if isinstance(usage, dict) and usage.get("total_tokens"):
        return int(usage["total_tokens"])

    # 2. LLMResult.llm_output.token_usage
    llm_output = getattr(response, "llm_output", None)
    if isinstance(llm_output, dict):
        tu = llm_output.get("token_usage") or llm_output.get("usage") or {}
        if isinstance(tu, dict) and tu.get("total_tokens"):
            return int(tu["total_tokens"])

    # 3. LLMResult.generations[0][0].message.usage_metadata
    generations = getattr(response, "generations", None)
    if generations and isinstance(generations, list) and generations:
        first = generations[0]
        if first and isinstance(first, list) and first:
            msg = getattr(first[0], "message", None) or first[0]
            um = getattr(msg, "usage_metadata", None)
            if isinstance(um, dict) and um.get("total_tokens"):
                return int(um["total_tokens"])

    return 0


class TokenAccumulatorHandler(BaseCallbackHandler):
    """LangChain 回调: 每次 LLM 调用结束时累加 token 到 per-request ContextVar.

    通过 graph config={"callbacks": [TokenAccumulatorHandler()]} 注入 create_react_agent,
    捕获 ReAct 循环中所有 LLM 调用 (Thought/Action/Final Answer) 的 usage, 累加为单请求总量。
    """

    def on_llm_end(self, response: Any, **kwargs: Any) -> None:
        """LLM 调用结束钩子: 提取 usage 并累加到 ContextVar."""
        try:
            tokens = _extract_usage_from_response(response)
            if tokens > 0:
                _token_total.set(_token_total.get() + tokens)
        except Exception as e:  # noqa: BLE001
            # 累加失败不阻断业务 (token 统计为可观测辅助, 非核心链路)
            logger.warning(f"token_accumulate_failed error={e}")
