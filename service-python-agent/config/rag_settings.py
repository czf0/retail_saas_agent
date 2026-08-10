"""
config/rag_settings.py
【新增】RAG 向量库、召回、重排独立配置。
承载向量化、向量库、分块、召回、融合、重排全链路参数。
"""
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class RAGSettings(BaseSettings):
    """RAG 检索增强相关配置项。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # 向量化模型名称
    RAG_EMBED_MODEL: str = "embedding-3"
    # 向量化服务地址
    RAG_EMBED_BASE_URL: str = "https://open.bigmodel.cn/api/paas/v4"
    # 嵌入提供者：openai_compatible（默认，复用 LLM 同端点的嵌入 API）/ huggingface（进程内本地 BGE）
    RAG_EMBED_PROVIDER: str = "openai_compatible"
    # 单次嵌入请求最大条数（OpenAI 兼容端点通常限制单次条数，如智谱 embedding-3 上限 64 条）。
    # langchain_openai 按 token 数分批（chunk_size=1000），短文档分块很多时单次请求会超限，
    # 因此在构建侧按条数二次分批，避免调用方报"input数组最大不得超过64条"。
    RAG_EMBED_MAX_BATCH: int = 64
    # 向量维度
    RAG_VECTOR_DIM: int = 768
    # 向量库类型：chroma（默认跨平台嵌入式）/ milvus_lite（Linux/Mac）/ faiss（纯内存）
    RAG_VECTOR_STORE_TYPE: str = "chroma"
    # Chroma 持久化目录
    RAG_CHROMA_PATH: str = "./data/chroma"
    # Milvus Lite 本地数据库文件路径（Linux/Mac 部署时启用）
    RAG_MILVUS_LITE_PATH: str = "./data/milvus_lite.db"
    # Milvus 服务模式地址（如配置为 server 模式时使用，预留）
    RAG_MILVUS_SERVER_URI: str = "http://127.0.0.1:19530"
    # Milvus 连接地址（预留）
    RAG_MILVUS_HOST: str = "127.0.0.1"
    # Milvus 端口（预留）
    RAG_MILVUS_PORT: int = 19530
    # 关键词检索召回数量
    RAG_KEYWORD_TOPK: int = 10
    # 向量检索召回数量
    RAG_VECTOR_TOPK: int = 10
    # RRF 融合算法参数 k
    RAG_RRF_K: int = 60
    # 重排后最终保留文档数
    RAG_RERANK_TOPK: int = 5
    # 重排模型服务地址（预留）
    RAG_RERANK_MODEL_URL: str = "http://127.0.0.1:8888/api/v1/rerank"
    # 文档分块大小（字符）
    RAG_CHUNK_SIZE: int = 200
    # 分块重叠（字符）
    RAG_CHUNK_OVERLAP: int = 40
    # 向量相似度距离阈值 (L2 distance, 越小越相似; 超过此值视为不相关丢弃, 评审 B1)
    # 默认 1.2 为保守值, 实际需按 embedding 模型校准 (bge-small-zh 的 L2 范围约 0.3~1.5)
    RAG_SIMILARITY_THRESHOLD: float = 1.2
    # 上下文 token 预算 (评审 D2, 防止检索结果挤占 LLM 生成预算)
    RAG_CONTEXT_TOKEN_BUDGET: int = 2000

    # 语义去重开关 (评审 P2-B4): RRF 融合后基于 embedding 余弦相似度合并近重复 chunk,
    # 避免跨文档同义段落重复注入挤占 token 预算. 关闭则仅靠 chunk_key 精确去重.
    RAG_SEMANTIC_DEDUP_ENABLED: bool = True
    # 语义去重余弦相似度阈值 (评审 P2-B4): 超过此值视为近重复, 合并分数与来源.
    # 0.92 为 bge-small-zh 经验值: 同义改写通常 >0.90, 仅共主题但不同信息点通常 <0.88.
    # 调低更激进去重 (易误合并), 调高更保守 (漏合并). 建议区间 0.88~0.95.
    RAG_SEMANTIC_DEDUP_THRESHOLD: float = 0.92

    # 表格感知分块开关 (评审 P2-C4): ingest 时识别 Markdown 表格并整块保留,
    # 避免表头与数据行被 RecursiveCharacterTextSplitter 切断导致语义丢失.
    # 关闭则回退纯 RecursiveCharacterTextSplitter.
    RAG_TABLE_AWARE_SPLIT: bool = True

    # ---- D4 语义分块 (结构+语义结合) ----
    # 语义分块总开关: 开启后 ingest 使用 SemanticStructureSplitter (结构切分+embedding 断句),
    # 关闭则回退 TableAwareSplitter (固定字符切分). 默认开启.
    RAG_SEMANTIC_CHUNK_ENABLED: bool = True
    # 语义断句余弦相似度阈值: 相邻句子 embedding 余弦距离超过此值视为语义跳变点, 在此处切分.
    # 0.5 为通用经验值: 同主题连续表述通常 <0.3, 主题切换通常 >0.5.
    # 调低更激进展切 (碎块多), 调高更保守 (长块多). 建议区间 0.4~0.7.
    RAG_SEMANTIC_BREAKPOINT_THRESHOLD: float = 0.5
    # 最小分块字符数: 语义切分产生的块若短于此值, 与相邻块合并 (避免碎片化, 保证块内信息量).
    RAG_MIN_CHUNK_SIZE: int = 100
    # 最大分块字符数: 超过此值的块触发第二层语义断句 (embedding 相似度切分);
    # 未超此值的块直接保留 (结构切分已足够, 无需 embedding 开销).
    RAG_MAX_CHUNK_SIZE: int = 300

    # ---- P1 RAG 运维可调参数 (消除散落硬编码) ----
    # BM25 chunks 持久化目录 (D1: 重启不丢, 增量追加, 按租户隔离文件 tenant_{id}.pkl)
    BM25_DIR: str = "./data/bm25"
    # 检索缓存条目上限 (进程内缓存, 超限触发过期清理防内存无限增长)
    RAG_CACHE_MAX_ENTRIES: int = 500
    # 检索缓存默认 TTL (秒, 知识库 ingest/delete 时主动失效该租户全部缓存)
    RAG_CACHE_TTL: float = 300.0
    # 同义词本地缓存 TTL (秒, 降 Redis RTT; 30s 平衡实时性与性能, Java 变更通知时主动清)
    SYNONYM_LOCAL_TTL: float = 30.0
    # 本地兜底重排 RRF 分权重 (LocalScoreReranker: score = w_rrf*rrf_norm + w_hit*hit_rate)
    RERANK_LOCAL_RRF_WEIGHT: float = 0.5
    # 本地兜底重排词命中率权重 (与 RERANK_LOCAL_RRF_WEIGHT 配对, 二者和建议为 1.0)
    RERANK_LOCAL_HIT_WEIGHT: float = 0.5
    # 查询改写扩展变体上限 (控制多路检索路数, 避免变体过多增加 embedding/BM25 延迟)
    QUERY_REWRITER_MAX_VARIANTS: int = 3
    # token 估算系数 (中文 ~2 字符/token, _assemble 按此粗估累加并按 token 预算截断)
    TOKEN_ESTIMATE_CHARS_PER_TOKEN: int = 2


# 全局 RAG 配置单例
rag_settings = RAGSettings()
