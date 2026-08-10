"""
core/exception.py
分层自定义异常 + 全局错误码枚举。
拆分系统异常、租户异常、远程调用异常、LLM 调用异常、RAG 专属异常、工具异常；
错误码与 Java 后端 ErrCodeEnum 完全对齐 (三端统一)。

编码规则: 成功 = 200 (HTTP 标准); 错误码 5 位分段 (XXYYY):
  10xxx — 系统级    20xxx — 认证鉴权    30xxx — 租户/门店
  40xxx — 工具调用   50xxx — LLM         60xxx — RAG
  70xxx — Agent 编排
"""


class ErrorCode:
    """全局错误码枚举，与 Java ErrCodeEnum 一一对应。"""

    # ---- 通用成功 ----
    SUCCESS = 200
    SUCCESS_MSG = "success"

    # ---- 系统级异常 (1xxxx) ----
    SYSTEM_INTERNAL_ERROR = 10001
    SYSTEM_BUSY = 10002
    PARAM_INVALID = 10003
    PARAM_TYPE_MISMATCH = 10004

    # ---- 认证鉴权异常 (2xxxx) ----
    AUTH_NOT_LOGIN = 20001
    AUTH_PERMISSION_DENIED = 20002
    AUTH_ROLE_DENIED = 20003

    # ---- 租户/门店异常 (3xxxx) ----
    TENANT_MISSING = 30001
    TENANT_FORBIDDEN = 30002
    TENANT_STORE_MISSING = 30003

    # ---- 工具异常 (4xxxx) ----
    TOOL_NOT_FOUND = 40001
    TOOL_DISABLED = 40002
    TOOL_PERMISSION_DENIED = 40003
    TOOL_PARAM_INVALID = 40004
    TOOL_EXEC_ERROR = 40005
    TOOL_TIMEOUT = 40006
    TOOL_CIRCUIT_OPEN = 40007
    TOOL_HITL_REJECTED = 40008
    TOOL_HITL_NO_CONTEXT = 40009
    TOOL_REMOTE_TIMEOUT = 40010
    TOOL_REMOTE_FAILED = 40011
    TOOL_GATEWAY_ERROR = 40012

    # ---- LLM 调用异常 (5xxxx) ----
    LLM_CALL_FAILED = 50001
    LLM_TIMEOUT = 50002
    LLM_RATE_LIMIT = 50003
    LLM_CONTEXT_TOO_LONG = 50004

    # ---- RAG 专属异常 (6xxxx) ----
    RAG_RETRIEVE_FAILED = 60001

    # ---- Agent 编排异常 (7xxxx) ----
    AGENT_STREAM_ERROR = 70001
    AGENT_RESUME_ERROR = 70002
    AGENT_SKILL_ERROR = 70003
    AGENT_BLOCKED = 70004


# ---- 面向用户的友好消息 (与 Java ErrCodeEnum.msg 对齐) ----
_ERROR_MESSAGES = {
    ErrorCode.SYSTEM_INTERNAL_ERROR: "服务暂时不可用，请稍后重试",
    ErrorCode.SYSTEM_BUSY: "系统繁忙，请稍后重试",
    ErrorCode.PARAM_INVALID: "提交的信息有误，请检查后重试",
    ErrorCode.PARAM_TYPE_MISMATCH: "提交的信息格式有误，请检查后重试",
    ErrorCode.AUTH_NOT_LOGIN: "登录已过期，请重新登录",
    ErrorCode.AUTH_PERMISSION_DENIED: "您没有执行此操作的权限",
    ErrorCode.AUTH_ROLE_DENIED: "您的账号角色无法执行此操作",
    ErrorCode.TENANT_MISSING: "租户信息缺失，请联系管理员",
    ErrorCode.TENANT_FORBIDDEN: "无权访问该租户的数据",
    ErrorCode.TENANT_STORE_MISSING: "门店信息缺失，请联系管理员",
    ErrorCode.TOOL_NOT_FOUND: "该功能暂不可用",
    ErrorCode.TOOL_DISABLED: "该功能已下线",
    ErrorCode.TOOL_PERMISSION_DENIED: "您没有使用此功能的权限",
    ErrorCode.TOOL_PARAM_INVALID: "功能参数有误，请检查输入",
    ErrorCode.TOOL_EXEC_ERROR: "操作执行失败，请稍后重试",
    ErrorCode.TOOL_TIMEOUT: "操作处理超时，请稍后重试",
    ErrorCode.TOOL_CIRCUIT_OPEN: "该功能暂时不可用，请稍后重试",
    ErrorCode.TOOL_HITL_REJECTED: "操作已取消",
    ErrorCode.TOOL_HITL_NO_CONTEXT: "该操作需要通过对话发起",
    ErrorCode.TOOL_REMOTE_TIMEOUT: "服务处理超时，请稍后重试",
    ErrorCode.TOOL_REMOTE_FAILED: "服务暂时不可用，请稍后重试",
    ErrorCode.TOOL_GATEWAY_ERROR: "服务返回异常，请稍后重试",
    ErrorCode.LLM_CALL_FAILED: "AI 服务暂时不可用，请稍后重试",
    ErrorCode.LLM_TIMEOUT: "AI 响应超时，请稍后重试",
    ErrorCode.LLM_RATE_LIMIT: "当前提问人数较多，请稍后重试",
    ErrorCode.LLM_CONTEXT_TOO_LONG: "对话内容过长，请开启新对话",
    ErrorCode.RAG_RETRIEVE_FAILED: "知识检索失败，请稍后重试",
    ErrorCode.AGENT_STREAM_ERROR: "生成回答时遇到问题，请重试",
    ErrorCode.AGENT_RESUME_ERROR: "恢复执行时遇到问题，请重新发起对话",
    ErrorCode.AGENT_SKILL_ERROR: "处理您的问题时遇到异常，请重试",
    ErrorCode.AGENT_BLOCKED: "您的请求被拦截，请调整后重试",
}


def get_user_message(code: int, fallback: str = "操作失败，请稍后重试") -> str:
    """根据错误码获取面向用户的友好消息。

    Args:
        code: 错误码 (与 Java ErrCodeEnum 对齐)
        fallback: 未命中映射时的兜底消息
    Returns:
        面向用户的提示文本 (不含技术细节)
    """
    return _ERROR_MESSAGES.get(code, fallback)


class BaseAppException(Exception):
    """应用异常基类，统一携带错误码与提示。"""

    def __init__(self, code: int, message: str, cause: Exception = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.cause = cause

    def __str__(self):
        return f"[{self.code}] {self.message}"


class SystemException(BaseAppException):
    """系统级异常。"""

    def __init__(self, message: str = "服务暂时不可用，请稍后重试",
                 code: int = ErrorCode.SYSTEM_INTERNAL_ERROR, cause: Exception = None):
        super().__init__(code, message, cause)


class TenantException(BaseAppException):
    """租户/权限异常。"""

    def __init__(self, message: str = "租户信息缺失，请联系管理员",
                 code: int = ErrorCode.TENANT_MISSING, cause: Exception = None):
        super().__init__(code, message, cause)


class RemoteCallException(BaseAppException):
    """远程调用异常（Java 后端透传、外部 HTTP）。"""

    def __init__(self, message: str = "服务暂时不可用，请稍后重试",
                 code: int = ErrorCode.TOOL_REMOTE_FAILED, cause: Exception = None):
        super().__init__(code, message, cause)


class LLMException(BaseAppException):
    """LLM 调用异常。"""

    def __init__(self, message: str = "AI 服务暂时不可用，请稍后重试",
                 code: int = ErrorCode.LLM_CALL_FAILED, cause: Exception = None):
        super().__init__(code, message, cause)


class RagException(BaseAppException):
    """RAG 检索专属异常。"""

    def __init__(self, message: str = "知识检索失败，请稍后重试",
                 code: int = ErrorCode.RAG_RETRIEVE_FAILED, cause: Exception = None):
        super().__init__(code, message, cause)


class ToolException(BaseAppException):
    """工具调用异常。"""

    def __init__(self, message: str = "操作执行失败，请稍后重试",
                 code: int = ErrorCode.TOOL_EXEC_ERROR, cause: Exception = None):
        super().__init__(code, message, cause)
