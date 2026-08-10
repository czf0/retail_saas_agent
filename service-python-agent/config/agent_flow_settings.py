"""
config/agent_flow_settings.py
Agent 编排、流程、工具全局参数。
承载三大范式循环/拆分上限、工具容错切面参数、RAG 默认开关。
"""
from typing import Dict, List

from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class AgentFlowSettings(BaseSettings):
    """Agent 编排与工具调度配置项。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ReAct 思考循环最大次数，避免死循环；LLMRateLimitNode 读取此值写入 llm_budget，
    # unified_agent/graph.py 读取 budget["react_max_iterations"] 决定 recursion_limit。
    REACT_MAX_ITERATIONS: int = 5
    # Plan&Executor 单次最大子任务数（legacy flow_architecture 范式使用）
    PLAN_MAX_SUBTASKS: int = 8
    # unified_agent Plan 生成最大任务数；LLMRateLimitNode 读取此值写入 budget["plan_max_tasks"]，
    # graph.py 的 _plan_generate_node 读取该 budget 限制任务清单规模。区别于 PLAN_MAX_SUBTASKS
    # （legacy 子任务数），本字段对应 unified 范式的"参考任务清单"上限。
    PLAN_MAX_TASKS: int = 5
    # 单请求 LLM Token 预算（预留）：LLMRateLimitNode 写入 budget["token_budget"]，
    # 当前执行器暂不消费，供后续按租户/角色差异化限流与成本核算使用。
    LLM_TOKEN_BUDGET_PER_REQUEST: int = 8000
    # WorkFlow 默认节点并行度
    WORKFLOW_PARALLELISM: int = 4
    # 工具调用统一超时（秒）
    TOOL_TIMEOUT: int = 30
    # 工具调用最大重试次数
    TOOL_MAX_RETRY: int = 2
    # 熔断阈值：连续失败次数达到后熔断
    TOOL_CIRCUIT_THRESHOLD: int = 5
    # 熔断恢复时间（秒）
    TOOL_CIRCUIT_RECOVER: int = 60
    # 默认是否开启 RAG 检索增强
    RAG_ENABLED: bool = True
    # 默认是否开启答案质量反思 (streaming 模式默认关闭, 成本高, 可通过环境变量 REFLECT_ENABLED 覆盖)
    REFLECT_ENABLED: bool = False

    # ---- Java @AgentTool 统一工具服务配置 (阶段3: Python 动态加载 Java 工具) ----
    # Java 后端基础地址复用 storage_settings.JAVA_BACKEND_BASE_URL, 此处仅配置工具接口路径与超时.
    # Java @AgentTool 统一调用接口路径 (POST, 传 business + operation + args + X-Trace-Id + X-Idempotency-Key)
    JAVA_TOOL_INVOKE_PATH: str = "/api/v1/agent/tools/invoke"
    # Java 工具注册表接口路径 (GET, 全量工具定义含 JSON Schema / destructive / outputHint)
    JAVA_TOOL_REGISTRY_PATH: str = "/api/v1/agent/tools/registry"
    # Java 角色可用工具接口路径 (GET, 当前用户可用工具白名单, Sa-Token 校验)
    JAVA_TOOL_ALLOWED_PATH: str = "/api/v1/agent/tools/allowed"
    # Java 工具调用 HTTP 超时 (秒, 含网络往返 + Java 反射执行 + 业务 Service 耗时)
    JAVA_TOOL_TIMEOUT: int = 15
    # 本地缓存工具定义 TTL (秒, 避免每请求拉取 /registry, 默认 5min; 与 RoleContextNode 缓存对齐)
    JAVA_TOOL_CACHE_TTL: int = 300

    # ---- P1 运维可调参数 (消除散落硬编码) ----
    # 统一 HTTP 客户端超时 (秒, 非 Java 工具的 HTTP 调用如 reranker 远程服务)
    HTTP_CLIENT_TIMEOUT: int = 10
    # 角色上下文降级时 ReAct 最大迭代数 (最小化权限升级风险, 降级后无工具白名单不应长时间运行)
    DEGRADED_MAX_ITERATIONS: int = 1
    # 短查询判定阈值 (字符, intent_router 规则判定: query < 此值 → need_plan=False)
    INTENT_SHORT_QUERY_THRESHOLD: int = 10
    # query→need_plan 缓存 TTL (秒, 合法结果才缓存避免抖变固化)
    INTENT_QUERY_CACHE_TTL: int = 600
    # query 缓存条目上限 (超限清理过期项, 防内存无限增长)
    INTENT_QUERY_CACHE_MAX: int = 1000
    # HITL pending TTL (秒, 被放弃的审批自动清理不泄漏 Redis 内存)
    HITL_PENDING_TTL: int = 3600
    # 短消息长度阈值 (interrupt 期间二分类: 超过此长度更可能是新查询而非审批回复)
    SHORT_MSG_THRESHOLD: int = 30

    # ---- P2 通用分页默认值 (消除 schema / 业务工具散落硬编码) ----
    # 默认页码 (PageResult / 业务查询工具入参 page 缺省值)
    DEFAULT_PAGE: int = 1
    # 默认每页条数 (PageResult / 业务工具 pageSize / format 截断行数 缺省值)
    DEFAULT_PAGE_SIZE: int = 20

    # ---- 长期记忆系统配置 (Java 管存储 SSOT, Python 管 AI 抽取/巩固/读取/注入) ----
    # 长期记忆总开关 (关闭则读取/抽取/巩固全部短路, 主流程零侵入)
    MEMORY_ENABLED: bool = True
    # 读取侧 LLM 选择的 top-K 条数 (注入 System prompt 的记忆条数上限)
    MEMORY_TOP_K: int = 5
    # 抽取/巩固/读取共用的小模型 (空串则回退主模型 LLM_MODEL; 新记忆必须经此模型降本)
    MEMORY_EXTRACT_MODEL: str = ""
    # 新记忆置信度门槛 (0~1): 低于该值直接丢弃, 不落库
    MEMORY_CONFIDENCE_THRESHOLD: float = 0.7
    # OTHER 分类槽位上限 (其他稳定偏好, 超限触发 consolidate 覆盖 importance 最低者)
    MEMORY_OTHER_SLOT_MAX: int = 3
    # 读取侧 LLM 选择调用超时 (毫秒, 独立于主流, 超时降级为短记忆缓存)
    MEMORY_READER_TIMEOUT_MS: int = 3000
    # 读取侧熔断阈值: 连续失败达到该次数后熔断, 短时间不再调 LLM
    MEMORY_READER_FAILURE_THRESHOLD: int = 3
    # 读取侧候选/选择结果缓存 TTL (秒, 降低重复查询 LLM 成本)
    MEMORY_SELECT_CACHE_TTL: int = 300
    # 注入 System prompt 的记忆最大 token 数 (超限按 记忆 < RAG < 短期历史 优先级裁剪)
    MEMORY_INJECT_MAX_TOKENS: int = 512
    # ---- Java 长期记忆 REST API 路径 (base 复用 storage_settings.JAVA_BACKEND_BASE_URL) ----
    # 读取候选记忆 (GET, 供 Reader 拉取当前用户候选记忆)
    MEMORY_JAVA_LIST_PATH: str = "/api/v1/agent/memory/list"
    # 抽取接口 (POST, Java 触发, 转发给 Python 抽取后回写) — 本函数为 Python 提供方, 路径见 api/memory_router.py
    # 巩固接口 (POST, Java 槽位溢出触发, 转发给 Python 巩固后回写) — 同上

    # ---- Prompt Optimization Constants (Batch A 新增配置项) ----
    # rag/memory/outputHint 组合注入 token 预算硬限 (字符数/4 作为估算代理, 暂无需真实 tokenizer)
    INJECT_TOKEN_BUDGET: int = 4000
    # judge 是否使用新的结构化 JSON 输出方法
    REFLECT_STRUCTURED: bool = False
    # revised_done chunk 追加开关 (默认关闭)
    REFLECT_REVISED_CHUNK_ENABLED: bool = False
    # plan_generate 是否使用新的结构化 prompt
    PLAN_STRUCTURED_ENABLED: bool = False
    # Prompt 版本号 (otel metrics 分桶标签, 文本变更时递增)
    PROMPT_VERSION: str = "v1.0.0"
    # 多版本口径数据字典 (默认给 10 个指标的 1 版数据)
    METRIC_CALIBERS: Dict[str, List[Dict[str, str]]] = {
        "GMV":        [{"effective_from": "0000-00-00", "formula": "已支付订单金额（不含退款/赠品/运费）", "freshness": "实时"}],
        "销量":        [{"effective_from": "0000-00-00", "formula": "已支付商品件数（不含退款）", "freshness": "实时"}],
        "库存周转天数": [{"effective_from": "0000-00-00", "formula": "平均库存 / 日均销量", "freshness": "T-1"}],
        "动销率":      [{"effective_from": "0000-00-00", "formula": "有销售SKU数 / 在售SKU数", "freshness": "T-1"}],
        "退货率":      [{"effective_from": "0000-00-00", "formula": "退货订单数 / 已支付订单数", "freshness": "T-1"}],
        "客单价":      [{"effective_from": "0000-00-00", "formula": "GMV / 支付订单数", "freshness": "实时"}],
        "毛利率":      [{"effective_from": "0000-00-00", "formula": "（GMV - 销售成本）/ GMV", "freshness": "T-1"}],
        "复购率":      [{"effective_from": "0000-00-00", "formula": "复购会员数 / 总购买会员数", "freshness": "T-1"}],
        "缺货率":      [{"effective_from": "0000-00-00", "formula": "缺货SKU数 / 在售SKU数", "freshness": "T-1"}],
        "连带率":      [{"effective_from": "0000-00-00", "formula": "销售件数 / 支付订单数", "freshness": "实时"}],
    }


# 全局编排配置单例
agent_flow_settings = AgentFlowSettings()

# ---- optimize-prompts spec Batch A 占位说明 ----
# 上述新增配置项 (INJECT_TOKEN_BUDGET / REFLECT_STRUCTURED / REFLECT_REVISED_CHUNK_ENABLED
# / PLAN_STRUCTURED_ENABLED / PROMPT_VERSION / METRIC_CALIBERS) 为 Prompt 优化规格 Batch A
# 引入, 供后续 Prompt 注入裁剪 / judge 结构化输出 / plan 结构化 / otel 指标回归对比使用.

