"""
main.py
项目唯一启动入口。
仅暴露流式对话接口 /api/v1/agent/stream/chat + /stream/resume,
编排器承载全生命周期, main.py 仅做 SSE 包装.
"""
from typing import Optional

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sse_starlette.sse import EventSourceResponse

from api.kb_sync_router import router as kb_sync_router
from api.kb_parse_router import router as kb_parse_router
from config.base_settings import base_settings
from config.validation import validate_required_settings
from core.exception import BaseAppException
from core.middleware import ContextMiddleware
from core.response import R
from core.logger import get_logger
from schema.agent_schema import ChatRequest
from pydantic import BaseModel, Field

# ---- 编排器后端: 锁定 new_agent (对象化重构版, 复刻 unified_agent 行为) ----
# /stream/chat 主流程与 /stream/resume (HITL 恢复) 均走 new_agent_orchestrator:
# 主流程 Preflight → CapabilityPipeline → ReactExecutor; HITL 恢复委托 UnifiedGraph.astream_resume.
from new_agent import new_agent_orchestrator
from core.obs.metrics import otel_metrics
from core.obs.otel_setup import instrument_app   # FastAPI 请求级自动插桩
# 注册可观测可视化路由 (审计列表/重放 + 工具统计, obs 可视化模块)
from core.obs.dashboard import router as obs_dashboard_router
logger = get_logger("main")
logger.info("使用 new_agent (对象化重构版, 复刻 UnifiedOrchestrator 行为)")

# ---- 启动配置校验 (fail-fast): 必配项缺失或为占位符则阻止启动 ----
# 校验两级: 通用必配 (LLM_API_KEY / INTERNAL_SECRET) + 生产环境必配 (Redis密码 / Java地址 / OTel导出器).
# 失败则打印清晰缺失项并 sys.exit(1), 避免运行时才暴露配置缺失 (原问题: 零必填校验, .env 缺失也能启动).
try:
    validate_required_settings()
    logger.info("启动配置校验通过")
except RuntimeError as _cfg_err:
    import sys
    print(f"FATAL: {_cfg_err}", flush=True)
    sys.exit(1)

# Layer 4 启动工具同步 (Java SSOT): 拉取 Java /tools/registry 全量工具定义
# populate 到 dynamic_java_tool_loader 缓存，并应用 Java 侧 enabled=0 禁用状态。
# Java 不可用时跳过 (降级, 不影响启动), 后续首次请求时
# load_langchain_tools 会回退原生 Python 工具。
try:
    from new_agent.tool_registry_sync import run_startup_tool_sync
    run_startup_tool_sync()
except Exception as _registry_err:  # noqa: BLE001
    logger.warning(f"启动工具同步异常 (跳过, 不阻断启动) err={_registry_err}")

# 创建 FastAPI 应用
app = FastAPI(
    title=base_settings.SERVICE_NAME,
    version="1.0.0",
    description="零售SaaS service-python-agent 通用底层AI框架",
)
# 注册全局链路上下文中间件
app.add_middleware(ContextMiddleware)
# OTel FastAPI 请求级自动插桩 (obs 可视化补全):
# 启用后每个 HTTP 请求自动产生 server span, 含路由/状态码/耗时,
# 与业务手动 span (unified:run / lc_tool:* 等) 拼成完整调用树.
# 顺序约束: 必须在 app 创建后、所有 include_router 之前调用, 否则已注册路由不被插桩.
instrument_app(app)
# 注册知识库同步路由 (Java → Python 知识文档变更通知, 内部服务间调用)
app.include_router(kb_sync_router)
# 注册知识库文件解析路由 (Java → Python 上传文件解析为文本, D2 文件上传管控)
app.include_router(kb_parse_router)
# 注册长期记忆抽取/巩固路由 (Java 每次 chat stream 结束后异步触发, 内部服务间调用)
from api.memory_router import router as memory_router
app.include_router(memory_router)

app.include_router(obs_dashboard_router)


@app.exception_handler(BaseAppException)
async def app_exception_handler(request: Request, exc: BaseAppException):
    """全局业务异常处理，统一返回 R 结构。"""
    logger.error(f"全局业务异常 code={exc.code} msg={exc.message}", exc_info=True)
    return JSONResponse(status_code=200, content=R.fail(code=exc.code, msg=exc.message).model_dump())


@app.exception_handler(Exception)
async def system_exception_handler(request: Request, exc: Exception):
    """全局系统异常处理。"""
    logger.error(f"全局系统异常 err={exc}", exc_info=True)
    return JSONResponse(status_code=200, content=R.from_exception(exc).model_dump())


@app.get("/health")
async def health():
    """健康检查接口（非业务，仅运维探活）。"""
    return R.ok(data={"status": "UP", "service": base_settings.SERVICE_NAME})


@app.get("/api/v1/agent/tools")
async def list_tools():
    """查询已注册工具列表（运维/调试用，非业务）。"""
    from tool.base.tool_registry import tool_registry
    return R.ok(data=tool_registry.list_tools())


@app.get("/api/v1/agent/skills")
async def list_skills():
    """查询已注册 Skill 列表 (运维/调试用, 非业务, Layer 3).

    仅 unified_agent 后端有 Skill.
    """
    from skill.base.skill_registry import skill_registry
    return R.ok(data=skill_registry.list_skills())


@app.get("/api/v1/agent/metrics")
async def get_metrics():
    """查询内存指标快照（运维/调试用，非业务）。

    评审 A3 修正: 原读原生 obs.metrics (业务埋点不在其中, 返回空), 改读 otel_metrics,
    覆盖 llm/tool/rag/orchestrator/governance 全部业务指标.
    """
    return R.ok(data=otel_metrics.snapshot())


@app.get("/metrics")
async def prometheus_metrics():
    """Prometheus exposition 格式指标暴露 (评审 E1).

    供 Prometheus 直接 scrape, 无需额外部署 OTel Collector 转 Prom.
    counter 输出累加值, histogram 输出 count+sum+avg.
    """
    lines = []
    seen_help = set()
    for entry in otel_metrics.snapshot():
        name = entry["name"]
        mtype = entry["type"]
        if name not in seen_help:
            lines.append(f"# HELP {name} {mtype}")
            lines.append(f"# TYPE {name} {mtype}")
            seen_help.add(name)
        labels = ",".join(f'{k}="{v}"' for k, v in entry.get("tags", {}).items())
        label_str = f"{{{labels}}}" if labels else ""
        if mtype == "histogram":
            lines.append(f"{name}_count{label_str} {entry.get('count', 0)}")
            lines.append(f"{name}_sum{label_str} {entry.get('sum', 0)}")
        else:
            lines.append(f"{name}{label_str} {entry['value']}")
    return JSONResponse(content="\n".join(lines) + "\n", media_type="text/plain")


@app.post("/api/v1/agent/stream/chat")
async def stream_chat(request: Request, chat: ChatRequest):
    """
    唯一流式对话接口。
    编排器封装主流程全生命周期 (会话校验/历史加载/request_id生成/span埋点/
    done聚合/会话持久化/异常兜底); main.py 仅做 SSE 包装.
    """
    logger.info(f"流式对话请求 session={chat.session_id} query={chat.query}")

    async def event_generator():
        async for chunk in new_agent_orchestrator.stream_chat(
            query=chat.query,
            session_id=chat.session_id,
        ):
            # stream_chat 内部已同步 chunk.session_id; 此处作为二层次兜底
            # 仅在 session_id 为 None (未设置) 时兜底, 不覆盖显式空串 (如 SESSION_MISSING 场景)
            if chunk.session_id is None:
                chunk.session_id = chat.session_id or ""
            yield {"event": "message", "data": chunk.model_dump_json()}

    return EventSourceResponse(event_generator())


class ResumeRequest(BaseModel):
    """HITL 审批恢复请求 (用户审批破坏性工具调用后触发)."""

    # 会话 ID (与被中断的 graph thread_id 对齐, 用于从 RedisSaver 恢复状态)
    session_id: str = Field(description="会话ID")
    # 用户审批结果: True=批准执行, False=拒绝执行
    approved: bool = Field(description="是否批准执行")
    # 拒绝原因 (approved=False 时由前端传入, 喂回 LLM 供其调整方案)
    reason: Optional[str] = Field(default=None, description="拒绝原因")


@app.post("/api/v1/agent/stream/resume")
async def stream_resume(request: Request, req: ResumeRequest):
    """
    HITL 审批恢复流式接口.

    编排器封装 resume 全生命周期 (会话校验/decision构造/span埋点/done聚合/异常兜底);
    main.py 仅做 SSE 包装.
    """
    logger.info(f"流式恢复请求 session={req.session_id} approved={req.approved}")

    async def event_generator():
        async for chunk in new_agent_orchestrator.stream_resume_request(
            session_id=req.session_id,
            approved=req.approved,
            reason=req.reason or "",
        ):
            if chunk.session_id is None:
                chunk.session_id = req.session_id or ""
            yield {"event": "message", "data": chunk.model_dump_json()}

    return EventSourceResponse(event_generator())


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=base_settings.SERVICE_HOST,
        port=base_settings.SERVICE_PORT,
        reload=base_settings.DEBUG,
        log_level=base_settings.LOG_LEVEL.lower(),
    )
