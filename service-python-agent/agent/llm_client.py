"""
agent/llm_client.py
完整实现 LLM 三大调用范式：
① sync_chat：一次性同步返回完整回答
② stream_chat：生成器 SSE 流式分片输出
③ batch_async_chat：批量异步多会话调用骨架
采用 OpenAI 兼容协议，httpx 作为底层 HTTP 客户端，全程接入观测埋点。
"""
import asyncio
import json
from typing import AsyncGenerator, Dict, List, Optional

import httpx

from config.llm_settings import llm_settings
from core.exception import ErrorCode, LLMException
from core.logger import get_logger
from agent.obs.metrics import metrics
from agent.obs.tracer import tracer
from schema.agent_schema import ChatMessage

logger = get_logger("llm_client")


class LLMClient:
    """LLM 调用客户端，封装三大范式。"""

    def __init__(self):
        # 基础请求地址与认证头
        self._base_url = llm_settings.LLM_BASE_URL.rstrip("/")
        self._headers = {
            "Authorization": f"Bearer {llm_settings.LLM_API_KEY}",
            "Content-Type": "application/json",
        }

    def _build_payload(
        self,
        messages: List[ChatMessage],
        stream: bool,
        temperature: Optional[float],
        model: Optional[str],
        max_tokens: Optional[int] = None,
    ) -> dict:
        """构造 OpenAI 兼容请求体。"""
        return {
            "model": model or llm_settings.LLM_MODEL,
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "stream": stream,
            "temperature": temperature if temperature is not None else llm_settings.LLM_TEMPERATURE,
            "max_tokens": max_tokens or llm_settings.LLM_MAX_TOKENS,
        }

    # ---- ① 同步单次调用 ----
    def sync_chat(
        self,
        messages: List[ChatMessage],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> str:
        """一次性同步返回完整回答。"""
        with tracer.span("llm_sync_chat"):
            metrics.incr("llm_call_total", tags={"mode": "sync"})
            payload = self._build_payload(messages, stream=False, temperature=temperature, model=model)
            url = f"{self._base_url}/chat/completions"
            try:
                with httpx.Client(timeout=llm_settings.LLM_TIMEOUT) as client:
                    resp = client.post(url, headers=self._headers, json=payload)
                    resp.raise_for_status()
                    data = resp.json()
                answer = data["choices"][0]["message"]["content"]
                metrics.incr("llm_call_success", tags={"mode": "sync"})
                logger.info(f"LLM同步调用成功 model={payload['model']} answer_len={len(answer)}")
                return answer
            except httpx.TimeoutException as exc:
                metrics.incr("llm_call_error", tags={"mode": "sync", "type": "timeout"})
                raise LLMException("LLM同步调用超时", code=ErrorCode.LLM_TIMEOUT, cause=exc)
            except Exception as exc:
                metrics.incr("llm_call_error", tags={"mode": "sync", "type": "other"})
                raise LLMException(f"LLM同步调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)

    # ---- ② SSE 流式分片输出 ----
    async def stream_chat(
        self,
        messages: List[ChatMessage],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> AsyncGenerator[str, None]:
        """生成器 SSE 流式分片输出，逐 token 产出。"""
        with tracer.span("llm_stream_chat"):
            metrics.incr("llm_call_total", tags={"mode": "stream"})
            payload = self._build_payload(messages, stream=True, temperature=temperature, model=model)
            url = f"{self._base_url}/chat/completions"
            timeout = httpx.Timeout(llm_settings.LLM_TIMEOUT, connect=10.0)
            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    async with client.stream("POST", url, headers=self._headers, json=payload) as resp:
                        resp.raise_for_status()
                        async for line in resp.aiter_lines():
                            chunk = self._parse_sse_line(line)
                            if chunk:
                                yield chunk
                metrics.incr("llm_call_success", tags={"mode": "stream"})
                logger.info("LLM流式调用完成")
            except httpx.TimeoutException as exc:
                metrics.incr("llm_call_error", tags={"mode": "stream", "type": "timeout"})
                raise LLMException("LLM流式调用超时", code=ErrorCode.LLM_TIMEOUT, cause=exc)
            except Exception as exc:
                metrics.incr("llm_call_error", tags={"mode": "stream", "type": "other"})
                raise LLMException(f"LLM流式调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)

    @staticmethod
    def _parse_sse_line(line: str) -> Optional[str]:
        """解析单行 SSE 数据，返回增量文本。"""
        if not line or not line.startswith("data:"):
            return None
        data = line[5:].strip()
        if data == "[DONE]":
            return None
        try:
            obj = json.loads(data)
            choices = obj.get("choices") or []
            if choices:
                delta = choices[0].get("delta", {})
                content = delta.get("content")
                if content:
                    return content
        except Exception:
            return None
        return None

    # ---- ③ 批量异步多会话调用 ----
    async def batch_async_chat(
        self,
        sessions: List[List[ChatMessage]],
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> List[str]:
        """批量异步多会话调用骨架，并发受 LLM_BATCH_CONCURRENCY 控制。"""
        with tracer.span("llm_batch_async_chat"):
            metrics.incr("llm_call_total", tags={"mode": "batch"}, value=len(sessions))
            semaphore = asyncio.Semaphore(llm_settings.LLM_BATCH_CONCURRENCY)

            async def _call_one(msgs: List[ChatMessage]) -> str:
                async with semaphore:
                    # 批量内部复用流式拼接为完整文本
                    parts: List[str] = []
                    async for chunk in self.stream_chat(msgs, temperature=temperature, model=model):
                        parts.append(chunk)
                    return "".join(parts)

            try:
                results = await asyncio.gather(*[_call_one(s) for s in sessions])
                metrics.incr("llm_call_success", tags={"mode": "batch"}, value=len(results))
                logger.info(f"LLM批量调用完成 count={len(results)}")
                return results
            except LLMException:
                raise
            except Exception as exc:
                metrics.incr("llm_call_error", tags={"mode": "batch", "type": "other"})
                raise LLMException(f"LLM批量调用失败: {exc}", code=ErrorCode.LLM_CALL_FAILED, cause=exc)


# 全局 LLM 客户端单例
llm_client = LLMClient()
