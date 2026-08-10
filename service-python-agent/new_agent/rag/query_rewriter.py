"""
other_agent/rag/query_rewriter.py
查询改写: 基于同义词归一化 + 扩展, 提升口语化 query 的召回率.

设计说明 (评审 B2/D7):
- 零售用户 query 常口语化/省略 ("上周卖得咋样"), 原 query 与知识库文档 (书面/术语化)
  语义距离大, 向量召回 miss, BM25 对同义词无能为力;
- 同义词归一化: 变体 → canonical term ("昨日成交额" → "昨天 GMV");
- 扩展变体: 用同义词反向替换生成多个 query 变体, 多路检索后 RRF 融合;
- 保留原 query 做一路检索 (防归一化误伤, 如某同义词映射错误时原 query 仍可召回);
- 失败降级: 同义词为空/异常时只返回原 query.

复用: synonym_client.get_synonyms.
HyDE/多查询生成 (LLM 改写) 预留接口, 初版不启用 (避免额外 LLM 调用增延迟).
"""
from __future__ import annotations

from typing import Dict, List

from config.rag_settings import rag_settings
from core.logger import get_logger
from core.obs.metrics import otel_metrics
from new_agent.rag.synonym_client import get_synonyms

logger = get_logger("lc_query_rewriter")


class QueryRewriter:
    """查询改写器: 同义词归一化 + 扩展变体生成."""

    def rewrite(self, query: str, tenant_id: str, domain: str) -> List[str]:
        """改写 query, 返回多路检索用的 query 列表.

        Args:
            query: 用户原始 query.
            tenant_id: 租户 ID (拉取租户特定同义词).
            domain: 业务域 (拉取域特定同义词).

        Returns:
            query 变体列表, 首个为原 query (防归一化误伤), 后续为扩展变体.
            同义词为空时只返回 [原 query].
        """
        if not query:
            return []
        # 首个始终是原 query, 防归一化误伤
        queries: List[str] = [query]
        try:
            synonyms = get_synonyms(tenant_id, domain)
            if not synonyms:
                return queries
            # 归一化 query: 把同义词替换为 canonical term
            normalized = self._normalize(query, synonyms)
            if normalized and normalized != query:
                queries.append(normalized)
            # 生成扩展变体: 用 canonical 的同义词反向替换, 扩大 BM25 召回
            expanded = self._expand(query, synonyms)
            for v in expanded:
                if v not in queries:
                    queries.append(v)
            otel_metrics.observe("rag_query_rewrite_count", len(queries), tags={})
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"query_rewrite_failed_fallback_original: {exc}")
        return queries

    @staticmethod
    def _normalize(query: str, synonyms: Dict[str, List[str]]) -> str:
        """归一化: 把 query 中的同义词替换为 canonical term.

        synonyms 结构: {canonical: [syn1, syn2]}, 反向查找 syn → canonical 替换.
        """
        result = query
        for canonical, syns in synonyms.items():
            for syn in syns:
                if syn and syn in result:
                    result = result.replace(syn, canonical)
        return result

    @staticmethod
    def _expand(query: str, synonyms: Dict[str, List[str]]) -> List[str]:
        """扩展: 对 query 中出现的 canonical term, 用其同义词生成变体.

        每个出现的 canonical 用首个同义词替换生成一个变体, 控制变体数量避免检索路数过多.
        """
        variants: List[str] = []
        for canonical, syns in synonyms.items():
            if canonical in query and syns:
                # 用首个同义词替换 canonical, 生成一个变体
                variant = query.replace(canonical, syns[0], 1)
                if variant != query:
                    variants.append(variant)
        # 限制扩展变体数量, 避免检索路数过多增加 embedding/BM25 延迟 (取自 rag_settings)
        return variants[:rag_settings.QUERY_REWRITER_MAX_VARIANTS]


# 全局查询改写器单例 (无状态, 可全局复用)
query_rewriter = QueryRewriter()
