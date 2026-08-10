"""
other_agent/rag/tokenizer.py
中文分词工具: 供 BM25 检索器和 LocalScoreReranker 共享.

设计说明:
- BM25Retriever 默认用英文正则 (\\w+) 分词, 中文被切成单字 (如 "库存预警" → ["库","存","预","警"]),
  丢失词语边界, 导致 BM25 词频/IDF 统计失真;
- LocalScoreReranker 的词命中率计算同样依赖分词, 英文正则对中文无法正确切分词语;
- 统一提取为共享模块, 确保检索器与重排器分词一致 (同一 query 在 BM25 召回和 reranker
  词命中度计算中使用相同 token 集合, 避免分词不一致导致的评分偏差).

复用: jieba (已声明在 requirements.txt), 零售中文场景必需.
"""
from typing import List

from core.logger import get_logger

logger = get_logger("lc_tokenizer")


def chinese_tokenize(text: str) -> List[str]:
    """中文分词: 优先 jieba, 失败降级为中文按字符 + 英文按词的混合切分.

    BM25Retriever 默认的 default_preprocessing 用正则 \\w+ 匹配, 对中文来说
    每个中文字符会被当作一个 token, 丢失词语边界信息 (如 "库存预警" 切成 4 个单字),
    导致 BM25 的 TF/IDF 统计粒度错误, 关键词召回精度大幅下降.

    jieba 分词能正确切分中文词语, 提升 BM25 对零售术语 (如 "盘点差异"、"促销满减")
    的精确匹配能力, 补充向量检索对精确术语召回不足的场景.

    Args:
        text: 待分词文本.

    Returns:
        分词后的 token 列表 (去空白, 小写化).
    """
    if not text:
        return []
    # 优先 jieba 分词 (已声明在 requirements.txt, 零售中文场景必需)
    try:
        import jieba
        return [w.lower() for w in jieba.lcut(text) if w.strip()]
    except ImportError:
        logger.warning("jieba 未安装, 降级为中文按字符 + 英文按词切分, 召回质量会下降")
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"jieba 分词异常, 降级为字符切分: {exc}")
    # 降级: 中文连续字符段、英文字母数字串各为一个 token
    import re
    return [w.lower() for w in re.findall(r"[\u4e00-\u9fff]+|[a-zA-Z0-9]+", text) if w.strip()]
