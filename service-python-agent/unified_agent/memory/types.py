"""
unified_agent/memory/types.py
长期记忆系统类型定义: MemoryCategory (整型枚举) + 记忆/操作数据模型.

设计说明 (对齐《长期记忆系统整改方案》):
- category 为整型枚举, 与 Java 侧 MemoryCategory 枚举值严格对齐 (0-6 核心分类 + 100=OTHER);
- 同分类固定槽位机制: category 0-6 每类 1 条, OTHER 允许 MEMORY_OTHER_SLOT_MAX 条;
  新记忆达置信度阈值时对同分类已存在记忆做 UPDATE 覆盖, 而非 ADD 新行;
- 抽取/巩固产出的操作统一为 MemoryOperation (add/update/delete), 由 Java 侧落库.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import IntEnum
from typing import List, Optional


class MemoryCategory(IntEnum):
    """长期记忆分类 (整型枚举, 与 Java 侧 MemoryCategory 枚举值对齐).

    0-6 为核心分类, 每类固定 1 条槽位; 100=OTHER 为其他稳定偏好, 允许 3 条槽位.
    """
    REPORT_FORMAT = 0        # 报表格式偏好 (周报/日报、同比环比、导出格式)
    SCOPE_FILTER = 1         # 数据/时间范围偏好 (只看本店、默认本月等)
    PERMISSION_CONFIRM = 2   # 权限/确认要求 (调价先确认、禁用批量)
    DIAGNOSIS_DEPTH = 3      # 诊断深度/回答长度 (要根因、先结论)
    DISPLAY_STYLE = 4        # 展示风格 (markdown 表、图表、纯文本)
    PROMO_PREFERENCE = 5     # 促销偏好 (满减>折扣、短视频推广等)
    COMMUNICATION_STYLE = 6  # 沟通风格 (正式/口语化、禁用表情)
    OTHER = 100              # 其他稳定偏好 (无法归类但稳定)

    @classmethod
    def from_code(cls, code: int) -> "MemoryCategory":
        """按整型 code 反查枚举, 非法值回退 OTHER."""
        try:
            return cls(code)
        except ValueError:
            return cls.OTHER

    @classmethod
    def is_core(cls, code: int) -> bool:
        """是否核心分类 (0-6, 每类固定 1 条槽位)."""
        return 0 <= code <= 6

    @classmethod
    def describe(cls, code: int) -> str:
        """返回分类的中文语义 (供 LLM prompt 描述)."""
        return {
            0: "报表格式偏好 (周报/日报、同比环比、导出格式)",
            1: "数据/时间范围偏好 (只看本店、默认本月、只看某品类)",
            2: "权限/确认要求 (调价/库存调整前先确认、禁用批量操作)",
            3: "诊断深度/回答长度 (要挖到根因、结论先给、回答要简短)",
            4: "展示风格 (markdown 表格、图表、纯文本)",
            5: "促销偏好 (满减优先于折扣、偏好短视频+导购话术)",
            6: "沟通风格 (正式语气、口语化、禁用表情)",
            100: "其他稳定偏好 (无法归入上述类的稳定偏好)",
        }.get(code, "其他稳定偏好")


@dataclass
class MemoryRecord:
    """一条长期记忆 (Java 侧 long_memory 表的一行快照)."""
    id: Optional[int] = None
    category: int = 0
    content: str = ""
    confidence: float = 0.0
    importance: int = 3          # 1-5 重要性 (1=随口一提, 5=硬性不可违背)
    access_count: int = 0
    last_accessed_at: Optional[str] = None
    memory_type: str = "preference"  # 预留: 记忆类型 (v1 仅 preference)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "memory_type": self.memory_type,
            "category": int(self.category),
            "content": self.content,
            "confidence": self.confidence,
            "importance": self.importance,
            "access_count": self.access_count,
            "last_accessed_at": self.last_accessed_at,
        }


@dataclass
class MemoryOperation:
    """对 Java 侧长期记忆库的一次写操作 (抽取/巩固产出, 由 Java 落库)."""
    op: str                       # add / update / delete
    category: int = 0
    content: str = ""
    confidence: float = 0.0
    importance: int = 3
    target_id: Optional[int] = None  # update/delete 时定位目标记忆 id

    def to_dict(self) -> dict:
        return {
            "op": self.op,
            "category": int(self.category),
            "content": self.content,
            "confidence": self.confidence,
            "importance": self.importance,
            "target_id": self.target_id,
        }


@dataclass
class ExtractResult:
    """一次抽取调用的结果 (Java 侧落库后返回)."""
    operations: List[MemoryOperation] = field(default_factory=list)
    ok: bool = True
    message: str = ""

    def to_dict(self) -> dict:
        return {
            "operations": [op.to_dict() for op in self.operations],
            "ok": self.ok,
            "message": self.message,
        }