"""
core/constants.py
跨系统协议常量集中管理.

设计说明:
- 协议常量是跨系统契约 (与 Java / W3C 严格对齐), 不外配到 .env, 集中到本文件统一管理;
- 消除散落在 core/context.py / obs/tracer.py 的硬编码, 修改协议常量只需改本文件;
- 与业务规则常量 (错误码 / 场景映射等) 的区别: 协议常量是技术契约 (不可随意改),
  业务常量随业务迭代 (保留在各业务模块).

常量分类:
1. W3C TraceContext 规范: trace_id / span_id 长度, traceparent 头名;
2. 项目协议头: X-Trace-ID 等 (Java↔Python 跨系统传递身份 / 链路标识, 改名需两端同步);
3. 本地标识前缀: 无上游标识时本地生成的 trace_id / span_id 前缀 (标记 local_only, 不回传 Java);
4. 默认标识: 默认租户 / 角色范围 (降级 / 兜底时使用).
"""

# ============================================================================
# W3C TraceContext 规范 (https://www.w3.org/TR/trace-context/)
# ============================================================================
# trace_id: 32 位小写 hex (16 字节), 全 0 视为非法
TRACE_ID_LEN = 32
# span_id: 16 位小写 hex (8 字节), 全 0 视为非法
SPAN_ID_LEN = 16
# W3C traceparent 头名 (格式: version-trace_id-span_id-flags)
TRACEPARENT_HEADER = "traceparent"

# ============================================================================
# 项目协议头 (Java↔Python 跨系统传递, 不可随意改名, 需两端同步)
# ============================================================================
# 链路标识 (优先 W3C traceparent, 回退以下项目协议头)
X_TRACE_ID = "X-Trace-ID"
X_SPAN_ID = "X-Span-ID"
# 身份标识 (Java 网关从 LoginUser 透传)
X_TENANT_ID = "X-Tenant-ID"
X_STORE_ID = "X-Store-ID"
X_USER_ID = "X-User-ID"
X_ROLE = "X-Role"
# 角色 ID (sys_role.id, 供 RAG 业务过滤按角色 ID 隔离文档可见性, D1.5)
X_ROLE_ID = "X-Role-Id"
# 内部调用密钥 (Python→Java 内部调用, Java 校验后建立临时登录态)
X_INTERNAL_SECRET = "X-Internal-Secret"
# 会话标识
X_SESSION_ID = "X-Session-ID"

# ============================================================================
# 本地标识前缀 (无上游标识时本地生成, 标记 local_only, 不回传 Java 下游)
# ============================================================================
# 本地临时 trace_id 前缀 (context.py 无上游 trace 时生成)
LOCAL_TRACE_PREFIX = "local-"
# 本地临时 span_id 前缀 (context.py 无上游 span 时生成, 与 Span 对象前缀区分)
LOCAL_SPAN_PREFIX = "local-span-"
# Span 对象 span_id 前缀 (obs/tracer.py 本地派生子 Span, 进程内使用)
SPAN_ID_PREFIX = "span-"

# ============================================================================
# 默认标识 (降级 / 兜底时使用, 非安全场景)
# ============================================================================
# 默认租户标识 (租户缺失时 preflight 阻断, 此值仅用于缓存 key 等非安全场景)
DEFAULT_TENANT_ID = "default"
# 默认角色ID (RAG 检索 / 缓存 key 中 role_id 缺失时兜底; "" 表示全员可见, 与 DB NULL 语义对齐)
DEFAULT_ROLE_ID = ""
