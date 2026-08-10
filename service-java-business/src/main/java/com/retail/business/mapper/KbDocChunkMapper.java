package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.KbDocChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档分片 Mapper (kb_doc_chunk 表 CRUD).
 * <p>
 * 供 KnowledgeDocController GET /kb/docs/{docId}/chunks 查询分片列表,
 * 以及 KnowledgeDocServiceImpl publish 时按 docId 删旧插新 (幂等 upsert).
 */
@Mapper
public interface KbDocChunkMapper extends BaseMapper<KbDocChunk> {
}
