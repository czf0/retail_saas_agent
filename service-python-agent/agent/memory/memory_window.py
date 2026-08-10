"""
agent/memory/memory_window.py
上下文窗口截断、Token 简易估算裁剪逻辑。
按 Token 预算从尾部保留最近消息，系统消息始终保留。
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
        return trimmed

    def total_tokens(self, messages: List[ChatMessage]) -> int:
        """估算消息列表总 Token。"""
        return sum(estimate_tokens(m.content) for m in messages)


# 全局窗口裁剪器单例
memory_window = MemoryWindow()
