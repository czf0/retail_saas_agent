"""
other_agent/llm/llm_client.py
基于 LangChain ChatOpenAI 封装 LLM 三大调用范式，与原生 agent.llm_client 接口对齐：
① sync_chat：同步单次返回完整回答（对应 ChatOpenAI.invoke）
② stream_chat：异步 SSE 流式分片输出（对应 ChatOpenAI.astream）
③ batch_async_chat：批量异步多会话调用（对应 ChatOpenAI.abatch）
复用 config/llm_settings 的 OpenAI 兼容端点配置；全程接入 OTel GenAI 语义埋点.

观测设计 (评审 B1/B2):
- 四范式统一提取 resp.usage_metadata (input_tokens/output_tokens/total_tokens),
  记为 llm_tokens_total 累加计数器 (供成本核算与限流器读取) + GenAI span 属性;
  解决原仅计次数无 token 的成本盲区, 为 LLM call limiting 提供前提数据;
- 四范式统一记录 llm_call_cost_ms histogram (P50/P95/P99 耗时分布),
  LLM 是 Agent 主要延迟源, 无 histogram 无法识别慢模型/慢 prompt;
- stream 启用 stream_usage=True, 流末尾 chunk 携带 usage, 累加后提取.
"""
import time
from typing import AsyncGenerator, List, Optional

from langchain_core.messages import AIMessage, AIMessageChunk, BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from config.llm_settings import llm_settings
from core.exception import ErrorCode, LLMException
from core.logger import get_logger
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer
from schema.agent_schema import ChatMessage

logger = get_logger("unified_llm_client")


def _to_lc_messages(messages: List[ChatMessage]) -> List[BaseMessage]:
    """将项目 ChatMessage 列表转换为 LangChain BaseMessage 列表。"""
    out: List[BaseMessage] = []
    for m in messages:
        if m.role == "system":
            out.append(SystemMessage(content=m.content))
        elif m.role == "assistant":
            out.append(AIMessage(content=m.content))
        else:
            # user / tool 统一作为 human 消息传入
            out.append(HumanMessage(content=m.content))
    return out


def _extract_usage(resp) -> dict:
    """从 LangChain 响应提取 token 使用量 (input/output/total), 无则返回空 dict.

    LangChain ChatOpenAI 在 resp.usage_metadata 返回 UsageMetadata (含 input_tokens/
    output_tokens/total_tokens); 兼容 resp.response_metadata.token_usage (OpenAI 原始字段).
    """
    usage = getattr(resp, "usage_metadata", None)
    if usage and isinstance(usage, dict):
        return {
            "input": int(usage.get("input_tokens", 0) or 0),
            "output": int(usage.get("output_tokens", 0) or 0),
            "total": int(usage.get("total_tokens", 0) or 0),
        }
    # 回退 OpenAI 原始 token_usage (部分模型/版本走此字段)
    meta = getattr(resp, "response_metadata", {}) or {}
    tu = meta.get("token_usage") or meta.get("usage") or {}
    if tu and isinstance(tu, dict):
        return {
            "input": int(tu.get("prompt_tokens", 0) or 0),
            "output": int(tu.get("completion_tokens", 0) or 0),
            "total": int(tu.get("total_tokens", 0) or 0),
        }
    return {}


def _record_usage(span, usage: dict, mode: str, model: str) -> None:
    """记录 token 使用到 span 属性 + metrics 累加计数器 + 日志.

    - span 属性: gen_ai.usage.input_tokens/output_tokens/total_tokens (OTel GenAI 语义);
    - metrics: llm_tokens_total 累加 (供成本核算与限流器读取, 评审 B1 闭环 LLM call limiting);
    - 仅在 usage 非空时记录, 避免空值噪声.
    """
    if not usage or not usage.get("total"):
        return
    span.set_attribute("gen_ai.usage.input_tokens", usage["input"])
    span.set_attribute("gen_ai.usage.output_tokens", usage["output"])
    span.set_attribute("gen_ai.usage.total_tokens", usage["total"])
    otel_metrics.incr(
        "llm_tokens_total",
        value=usage["total"],
        tags={"mode": mode, "model": model, "backend": "lc"},
    )


def _record_latency(cost_ms: float, mode: str, model: str, status: str) -> None:
    """记录 LLM 调用耗时到 histogram (评审 B2), 供 P50/P95/P99 分布分析."""
    otel_metrics.observe(
        "llm_call_cost_ms",
        cost_ms,
        tags={"mode": mode, "model": model, "status": status, "backend": "lc"},
    )


class UnifiedLLMClient:
    """LangChain ChatOpenAI 调用客户端，封装三大范式。"""

    def __init__(self):
        # 复用现有 LLM 配置（OpenAI 兼容端点，默认指向本地 Ollama）
        # stream_usage=True: 流式调用时末尾 chunk 携带 token 使用量 (评审 B1 stream 范式补全)
        self._chat = ChatOpenAI(
            base_url=llm_settings.LLM_BASE_URL,
            api_key=llm_settings.LLM_API_KEY,
            model=llm_settings.LLM_MODEL,
            max_tokens=llm_settings.LLM_MAX_TOKENS,
            temperature=llm_settings.LLM_TEMPERATURE,
            timeout=llm_settings.LLM_TIMEOUT,
            stream_usage=True,
        )

    def _bound(self, temperature: Optional[float], model: Optional[str]):
        """按入参覆盖温度/模型，返回绑定后的 Runnable。"""
        kwargs = {}
        if temperature is not None:
            kwargs["temperature"] = temperature
        if model:
            kwargs["model"] = model
        return self._chat.bind(**kwargs) if kwargs else self._chat

    # ---- ① 同步单次调用 ----
    def sync_chat(
        self,
        messages: List[ChatMessage],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> str:
        """一次性同步返回完整回答。"""
        used_model = model or llm_settings.LLM_MODEL
        with otel_tracer.span("unified_llm_sync_chat") as span:
            span.set_attribute("gen_ai.operation.name", "chat")
            span.set_attribute("gen_ai.request.model", used_model)
            otel_metrics.incr("llm_call_total", tags={"mode": "sync", "backend": "lc"})
            start = time.time()
            try:
                bound = self._bound(temperature, model)
                resp = bound.invoke(_to_lc_messages(messages))
                answer = resp.content if isinstance(resp.content, str) else str(resp.content)
                # GenAI 语义：响应模型 + token 使用 (评审 B1)
                span.set_attribute("gen_ai.response.model", getattr(resp, "response_metadata", {}).get("model", ""))
                _record_usage(span, _extract_usage(resp), "sync", used_model)
                _record_latency((time.time() - start) * 1000, "sync", used_model, "success")
                otel_metrics.incr("llm_call_success", tags={"mode": "sync", "backend": "lc"})
                logger.info(f"LC LLM同步调用成功 model={used_model} answer_len={len(answer)}")
                return answer
            except Exception as exc:
                _record_latency((time.time() - start) * 1000, "sync", used_model, "error")
                otel_metrics.incr("llm_call_error", tags={"mode": "sync", "backend": "lc", "type": "other"})
                span.record_exception(exc)
                raise LLMException(f"LC LLM同步调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)

    # ---- ①b 异步单次调用（不阻塞事件循环，用于并发场景如 Plan&Exec fan-out）----
    async def async_chat(
        self,
        messages: List[ChatMessage],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
        callbacks: Optional[List] = None,
    ) -> str:
        """异步一次性返回完整回答，使用 ainvoke 不阻塞事件循环。

        callbacks: 可选 LangChain BaseCallbackHandler 列表, 透传给 ainvoke(config).
        调用方传入 [TokenAccumulatorHandler()] 累加 per-request token,
        与 ReAct 路径 (graph 注入回调) 保持 token 统计口径一致.
        """
        used_model = model or llm_settings.LLM_MODEL
        with otel_tracer.span("unified_llm_async_chat") as span:
            span.set_attribute("gen_ai.operation.name", "chat")
            span.set_attribute("gen_ai.request.model", used_model)
            otel_metrics.incr("llm_call_total", tags={"mode": "async", "backend": "lc"})
            start = time.time()
            try:
                bound = self._bound(temperature, model)
                config = {"callbacks": callbacks} if callbacks else None
                resp = await bound.ainvoke(_to_lc_messages(messages), config=config)
                answer = resp.content if isinstance(resp.content, str) else str(resp.content)
                span.set_attribute("gen_ai.response.model", getattr(resp, "response_metadata", {}).get("model", ""))
                _record_usage(span, _extract_usage(resp), "async", used_model)
                _record_latency((time.time() - start) * 1000, "async", used_model, "success")
                otel_metrics.incr("llm_call_success", tags={"mode": "async", "backend": "lc"})
                logger.info(f"LC LLM异步调用成功 model={used_model} answer_len={len(answer)}")
                return answer
            except Exception as exc:
                _record_latency((time.time() - start) * 1000, "async", used_model, "error")
                otel_metrics.incr("llm_call_error", tags={"mode": "async", "backend": "lc", "type": "other"})
                span.record_exception(exc)
                raise LLMException(f"LC LLM异步调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)

    # ---- ② 异步流式分片输出 ----
    async def stream_chat(
        self,
        messages: List[ChatMessage],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> AsyncGenerator[str, None]:
        """异步生成器 SSE 流式分片输出，逐 token 产出。

        stream_usage=True 已在构造器开启, 末尾 chunk 携带 usage_metadata;
        本方法累加所有 chunk 后从聚合消息提取 token 使用 (评审 B1 stream 范式补全).
        """
        used_model = model or llm_settings.LLM_MODEL
        with otel_tracer.span("unified_llm_stream_chat") as span:
            span.set_attribute("gen_ai.operation.name", "chat")
            span.set_attribute("gen_ai.request.model", used_model)
            otel_metrics.incr("llm_call_total", tags={"mode": "stream", "backend": "lc"})
            start = time.time()
            aggregated: Optional[AIMessageChunk] = None
            try:
                bound = self._bound(temperature, model)
                async for chunk in bound.astream(_to_lc_messages(messages)):
                    # 累加 chunk 以便末尾提取 usage (AIMessageChunk 支持 + 合并)
                    if aggregated is None:
                        aggregated = chunk
                    else:
                        try:
                            aggregated = aggregated + chunk
                        except Exception:  # noqa: BLE001
                            aggregated = chunk
                    content = chunk.content
                    if isinstance(content, str) and content:
                        yield content
                # 流结束: 从聚合消息提取 usage
                if aggregated is not None:
                    _record_usage(span, _extract_usage(aggregated), "stream", used_model)
                _record_latency((time.time() - start) * 1000, "stream", used_model, "success")
                otel_metrics.incr("llm_call_success", tags={"mode": "stream", "backend": "lc"})
                logger.info("LC LLM流式调用完成")
            except Exception as exc:
                _record_latency((time.time() - start) * 1000, "stream", used_model, "error")
                otel_metrics.incr("llm_call_error", tags={"mode": "stream", "backend": "lc", "type": "other"})
                span.record_exception(exc)
                raise LLMException(f"LC LLM流式调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)

    # ---- ③ 批量异步多会话调用 ----
    async def batch_async_chat(
        self,
        sessions: List[List[ChatMessage]],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> List[str]:
        """批量异步多会话调用，并发由 LangChain abatch 内部控制。"""
        used_model = model or llm_settings.LLM_MODEL
        with otel_tracer.span("unified_llm_batch_async_chat") as span:
            span.set_attribute("gen_ai.operation.name", "batch")
            span.set_attribute("gen_ai.request.model", used_model)
            otel_metrics.incr("llm_call_total", tags={"mode": "batch", "backend": "lc"}, value=len(sessions))
            bound = self._bound(temperature, model)
            payloads = [_to_lc_messages(msgs) for msgs in sessions]
            start = time.time()
            try:
                results_raw = await bound.abatch(payloads)
                results = [
                    r.content if isinstance(r.content, str) else str(r.content) for r in results_raw
                ]
                # 批量累加各会话 token (评审 B1 batch 范式)
                batch_total = 0
                for r in results_raw:
                    batch_total += _extract_usage(r).get("total", 0)
                if batch_total > 0:
                    span.set_attribute("gen_ai.usage.total_tokens", batch_total)
                    otel_metrics.incr(
                        "llm_tokens_total",
                        value=batch_total,
                        tags={"mode": "batch", "model": used_model, "backend": "lc"},
                    )
                _record_latency((time.time() - start) * 1000, "batch", used_model, "success")
                otel_metrics.incr("llm_call_success", tags={"mode": "batch", "backend": "lc"}, value=len(results))
                logger.info(f"LC LLM批量调用完成 count={len(results)} tokens={batch_total}")
                return results
            except Exception as exc:
                _record_latency((time.time() - start) * 1000, "batch", used_model, "error")
                otel_metrics.incr("llm_call_error", tags={"mode": "batch", "backend": "lc", "type": "other"})
                span.record_exception(exc)
                raise LLMException(f"LC LLM批量调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)


# 全局 LC LLM 客户端单例
unified_llm_client = UnifiedLLMClient()
