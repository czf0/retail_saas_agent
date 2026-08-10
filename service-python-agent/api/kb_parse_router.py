"""
api/kb_parse_router.py
知识库文件解析 HTTP 路由: 暴露 /api/v1/kb/parse 接收 Java 转发的上传文件并解析为文本.

设计说明 (D2 文件上传管控, 决策 2: Python 端解析):
- 前端上传 MultipartFile → Java KnowledgeDocController POST /kb/docs/upload 接收;
- Java 将原始文件字节以 multipart 转发到本端点, Python 解析后返回 text;
- Java 拿到 text 后落盘 (file_path) + 生成 preview + 建草稿, 走原有 publish→kb_sync 链路;
- 不走业务 RBAC (内部服务间调用), 由网络层隔离 (与 kb_sync_router 同前缀 /api/v1/kb);
- 解析失败 (格式不支持/文件损坏) 返回 200 + ok=false + 友好消息, Java 记日志不建草稿.

请求体: multipart/form-data
    - file: 单个文件 (Java 逐文件转发, 每文件一次调用; 简化 Python 端并发解析复杂度)

响应体 (统一 R 结构, data 内含解析结果):
    {
        "code": 200, "msg": "成功", "traceId": "...",
        "data": {"ok": true, "text": "...", "page_count": 3,
                 "parse_engine": "pdfplumber", "char_count": 1234, "filename": "promo.pdf"}
    }

与 kb_sync_router 的区别:
- kb_sync_router 处理文档元数据同步 (ingest/delete), 不接触原始文件;
- 本路由处理原始文件解析, 返回纯文本供 Java 落盘与索引.
"""
from __future__ import annotations

from fastapi import APIRouter, File, UploadFile
from pydantic import BaseModel, Field

from core.response import R
from core.logger import get_logger
from new_agent.rag.file_parser import ALLOWED_EXTENSIONS, ParseError, parse_file

logger = get_logger("kb_parse_router")

# 知识库解析路由 (与 kb_sync_router 同前缀 /api/v1/kb, 内部服务间调用)
router = APIRouter(prefix="/api/v1/kb", tags=["kb-parse"])

# 单文件大小上限 (10MB, 与 Java 侧 kb.upload.max-size 对齐, 双重校验防绕过)
_MAX_FILE_SIZE = 10 * 1024 * 1024


class KbParseResult(BaseModel):
    """文件解析结果 (Python → Java, 供 Java 落盘与建草稿)."""

    ok: bool = Field(description="是否解析成功 (业务级, 非系统异常)")
    message: str = Field(default="", description="结果描述 (失败时为面向用户的友好消息)")
    text: str = Field(default="", description="提取的纯文本 (成功时返回)")
    page_count: int = Field(default=0, description="页数 (PDF 为页数, docx/txt 为 1)")
    parse_engine: str = Field(default="", description="解析引擎名 (pdfplumber/python-docx/plain_text)")
    char_count: int = Field(default=0, description="文本字符数")
    filename: str = Field(default="", description="原始文件名 (透传, 供日志关联)")


@router.post("/parse")
async def kb_parse(file: UploadFile = File(..., description="待解析的文件 (pdf/docx/txt/md)")):
    """接收上传文件, 按扩展名分发解析, 返回纯文本.

    - 业务级失败 (格式不支持/文件损坏/超大小) 返回 200 + ok=false + message;
    - 系统异常 (解析库崩溃) 同样返回 200 + ok=false + message, Java 侧记日志;
    - 成功返回 200 + ok=true + text (供 Java 落盘 file_path + 生成 preview + 建草稿).
    """
    filename = file.filename or "unknown"

    # 文件名/扩展名校验 (与 Java 侧白名单对齐, 双重校验防绕过)
    ext = "." + filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in ALLOWED_EXTENSIONS:
        msg = f"不支持的文件类型: {ext}, 仅支持 {', '.join(sorted(ALLOWED_EXTENSIONS))}"
        logger.warning(f"kb_parse_rejected filename={filename} ext={ext}")
        return R.ok(data=KbParseResult(ok=False, message=msg, filename=filename).model_dump())

    try:
        content = await file.read()
    except Exception as exc:  # noqa: BLE001
        logger.error(f"kb_parse_read_failed filename={filename} error={exc}", exc_info=True)
        return R.ok(data=KbParseResult(
            ok=False, message="文件读取失败, 请重试", filename=filename
        ).model_dump())

    # 大小校验 (与 Java 侧对齐, 双重校验防绕过)
    if len(content) > _MAX_FILE_SIZE:
        msg = f"文件过大: {len(content) // 1024 // 1024}MB, 上限 {_MAX_FILE_SIZE // 1024 // 1024}MB"
        logger.warning(f"kb_parse_rejected filename={filename} size={len(content)}")
        return R.ok(data=KbParseResult(ok=False, message=msg, filename=filename).model_dump())

    try:
        result = parse_file(filename, content)
    except ParseError as exc:
        # 解析失败: 面向用户的友好消息 (技术细节已在 parse_file 内入日志)
        logger.warning(f"kb_parse_failed filename={filename} msg={exc}")
        return R.ok(data=KbParseResult(
            ok=False, message=str(exc), filename=filename
        ).model_dump())

    logger.info(f"kb_parse_ok filename={filename} engine={result.parse_engine} chars={result.char_count}")
    return R.ok(data=KbParseResult(
        ok=True,
        message=f"解析成功: {result.char_count} 字符, {result.page_count} 页",
        text=result.text,
        page_count=result.page_count,
        parse_engine=result.parse_engine,
        char_count=result.char_count,
        filename=result.filename,
    ).model_dump())
