"""
unified_agent/memory/window.py
上下文窗口截断、Token 简易估算裁剪逻辑、MessageCompressor 摘要压缩。
按 Token 预算从尾部保留最近消息，系统消息始终保留。
压缩模式：trim 丢弃早期消息时，调用 LLM 生成一句话摘要注入 system 消息。

来源说明：从 agent/memory/memory_window.py 复制迁入 unified_agent/memory 包，
内部依赖均为共享基础设施（config.storage_settings / obs.logger / schema / utils），无需改动。
agent/memory/memory_window.py 原文件保留不动。
"""
from typing import List

from config.storage_settings import storage_settings
from core.logger import get_logger
from schema.agent_schema import ChatMessage
from utils.common_util import estimate_tokens

logger = get_logger("memory_window")


class MemoryWindow:
    """上下文窗口裁剪器。"""

    def __init__(self, max_tokens: int = None):
        self._max_tokens = max_tokens or storage_settings.SESSION_MAX_TOKENS

    def trim(self, messages: List[ChatMessage]) -> List[ChatMessage]:
        """
        按 Token 预算裁剪上下文：
        - system 消息始终保留在前；
        - 剩余预算内从最近消息向前保留；
        - 超出预算的早期消息被丢弃。
        """
        if not messages:
            return []

        # 分离 system 消息与对话消息
        system_msgs = [m for m in messages if m.role == "system"]
        dialog_msgs = [m for m in messages if m.role != "system"]

        system_tokens = sum(estimate_tokens(m.content) for m in system_msgs)
        budget = self._max_tokens - system_tokens

        if budget <= 0:
            # 仅保留 system，丢弃全部历史
            logger.warning(f"system消息Token已超预算 budget={self._max_tokens}，仅保留system")
            return system_msgs

        kept: List[ChatMessage] = []
        used = 0
        # 从最近消息向前保留
        for msg in reversed(dialog_msgs):
            t = estimate_tokens(msg.content)
            if used + t > budget:
                break
            kept.append(msg)
            used += t
        kept.reverse()

        trimmed = system_msgs + kept
        dropped = len(dialog_msgs) - len(kept)
        if dropped > 0:
            logger.info(f"上下文裁剪完成 保留={len(trimmed)} 丢弃={dropped} used_tokens={system_tokens + used}")
        return trimmed, dropped

    def total_tokens(self, messages: List[ChatMessage]) -> int:
        """估算消息列表总 Token。

        [当前主流程未调用] 预留上下文 Token 估算, 供管理端/监控诊断使用.
        """
        return sum(estimate_tokens(m.content) for m in messages)


class MessageCompressor:
    """历史对话摘要压缩器：trim 丢弃早期消息时，调用 LLM 生成摘要保留关键信息。

    设计说明：
    - 默认关闭（SESSION_COMPRESS_ENABLED=False），开启后灰度验证；
    - 摘要作为一条 system 角色消息注入，不干扰对话角色结构；
    - 压缩失败时静默降级（不阻断流程），仅日志告警；
    - 每次压缩前先移除旧摘要，确保始终只有一条最新摘要（避免多轮压缩累积）。
    """

    _SUMMARY_PREFIX = "[以下为历史对话摘要]"
    _COMPRESS_PROMPT = (
        "请用一句话概括以下历史对话的核心内容，包括用户提出的关键问题、"
        "助手给出的重要结论或数据。保留会话中出现的具体数值、指标和业务术语。"
    )
    _COMPRESS_WITH_OLD_PROMPT = (
        "以下是一段已压缩的历史对话摘要，以及新增的对话记录。\n"
        "请将已有摘要和新对话合并成一句新的摘要，保留所有关键信息。\n"
        "即使数据量较大，也要确保核心数值、指标和业务术语不丢失。"
    )

    @staticmethod
    def extract_old_summary(messages: List[ChatMessage]) -> str:
        """提取列表中已有的旧摘要内容（匹配 _SUMMARY_PREFIX 前缀），返回摘要文本（不含前缀）。

        同时返回移除旧摘要后的消息列表。用于调用方在压缩前先保存旧摘要内容，
        然后传给 compress 的 previous_summary 参数，实现"旧摘要 + 新丢弃消息 → 新摘要"的继承。
        """
        for m in messages:
            if m.role == "system" and m.content.startswith(MessageCompressor._SUMMARY_PREFIX):
                return m.content[len(MessageCompressor._SUMMARY_PREFIX):].strip()
        return ""

    @staticmethod
    def remove_old_summary(messages: List[ChatMessage]) -> List[ChatMessage]:
        """移除列表中已有的旧摘要消息（匹配 _SUMMARY_PREFIX 前缀）。

        保留一条最新摘要的唯一性，避免多轮压缩后累积多条 system 摘要。
        """
        return [m for m in messages if not (m.role == "system" and m.content.startswith(MessageCompressor._SUMMARY_PREFIX))]

    def compress(self, dropped_messages: List[ChatMessage], previous_summary: str = "") -> ChatMessage:
        """对丢弃的早期消息生成摘要，返回 system 角色摘要消息。

        使用 unified_llm_client.sync_chat 同步调用 LLM。
        摘要格式：`[以下为历史对话摘要] 用户之前询问了... 助手回答了...`

        Args:
            dropped_messages: 被 trim 丢弃的早期消息列表。
            previous_summary: 上一轮压缩的摘要内容（不含前缀），用于继承已有摘要信息。
                新摘要 = 旧摘要内容 + 新丢弃消息，一起压缩生成更完整的一条摘要。

        Returns:
            摘要后的 system 消息。若压缩失败，返回空内容的 system 消息（静默降级）。
        """
        if not dropped_messages:
            return ChatMessage(role="system", content="")

        try:
            from unified_agent.llm import unified_llm_client

            # 拼接压缩输入：旧摘要（如有）+ 新丢弃消息
            parts: List[str] = []
            if previous_summary:
                parts.append(f"[已有历史摘要] {previous_summary}")
                parts.append("")
            for m in dropped_messages:
                if m.content:
                    parts.append(f"{'用户' if m.role in ('user', 'human') else '助手'}: {m.content}")
            history_text = "\n".join(parts)

            prompt = self._COMPRESS_WITH_OLD_PROMPT if previous_summary else self._COMPRESS_PROMPT
            summary = unified_llm_client.sync_chat(
                [ChatMessage(role="system", content=prompt),
                 ChatMessage(role="user", content=history_text)],
                temperature=0.0,
            )
            summary_text = (summary or "").strip()
            if summary_text:
                has_old = " (含旧摘要)" if previous_summary else ""
                logger.info(f"历史摘要压缩完成{has_old} 丢弃={len(dropped_messages)} 摘要={summary_text[:60]}")
                return ChatMessage(role="system", content=f"{self._SUMMARY_PREFIX} {summary_text}")
        except Exception as exc:
            logger.warning(f"历史摘要压缩失败 丢弃={len(dropped_messages)} err={exc}")

        return ChatMessage(role="system", content="")


# 全局窗口裁剪器单例
memory_window = MemoryWindow()
