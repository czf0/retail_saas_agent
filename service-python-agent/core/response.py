"""
core/response.py
统一返回体 R。
字段 code(int)、msg(str)、data(any)、traceId(str) 与 Java 后端完全一致。
"""
from typing import Any, Optional

from pydantic import BaseModel, Field

from core.context import context_manager
from core.exception import ErrorCode


class R(BaseModel):
    """统一返回体，字段与 Java 后端 R 结构完全对齐。"""

    # 业务码：200 成功 (与 HTTP 标准对齐)，非 200 失败 (5 位错误码)
    code: int = Field(default=ErrorCode.SUCCESS, description="业务码")
    # 提示信息
    msg: str = Field(default=ErrorCode.SUCCESS_MSG, description="提示信息")
    # 业务数据
    data: Any = Field(default=None, description="业务数据")
    # 链路追踪 ID（仅当上游存在时填充，本地临时标识不对外暴露）
    traceId: Optional[str] = Field(default=None, description="链路追踪ID")

    @classmethod
    def ok(cls, data: Any = None, msg: str = ErrorCode.SUCCESS_MSG) -> "R":
        """构造成功返回体。"""
        return cls(code=ErrorCode.SUCCESS, msg=msg, data=data, traceId=cls._resolve_trace_id())

    @classmethod
    def fail(cls, code: int = ErrorCode.SYSTEM_INTERNAL_ERROR, msg: str = "失败", data: Any = None) -> "R":
        """构造失败返回体。"""
        return cls(code=code, msg=msg, data=data, traceId=cls._resolve_trace_id())

    @classmethod
    def from_exception(cls, exc: Exception) -> "R":
        """从异常构造失败返回体。"""
        from core.exception import BaseAppException
        if isinstance(exc, BaseAppException):
            return cls.fail(code=exc.code, msg=exc.message)
        return cls.fail(code=ErrorCode.SYSTEM_INTERNAL_ERROR, msg=str(exc) or "服务暂时不可用，请稍后重试")

    @staticmethod
    def _resolve_trace_id() -> Optional[str]:
        """
        解析对外暴露的 traceId。
        规则：仅当上游存在真实链路标识时返回；本地临时标识不对外暴露。
        """
        try:
            if context_manager.is_local_only():
                return None
            tid = context_manager.get_trace_id()
            return tid or None
        except Exception:
            return None
