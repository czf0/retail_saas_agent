package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 知识库文档分片列表行项(D1 chunk 可见性核对页;运营管理"文档分块明细" 20/页);展示分片序号/类型/头尾预览(中间内容省略占位避免传输冗余).
 * <p>统计口径:按 knowledge_doc.id 查询其下所有 chunk;列表按 chunkIndex 升序;全量正文查向量库/BM25 pkl 用 chunkId 回查.
 */
@Data
public class KbChunkItemResp {

    /** 分片唯一标识 ({doc_id}_{chunk_index}, 与向量库 metadata 对齐) */
    private String chunkId;

    /** 分片序号 (文档内从 0 递增) */
    private Integer chunkIndex;

    /** 分片头部文本 (前 2*overlap 字符, 管理员预览用) */
    private String contentHead;

    /** 分片尾部文本 (后 2*overlap 字符, 小分片为空) */
    private String contentTail;

    /** 分片全量字符数 (head+tail 截断前的原始长度, 供前端计算省略字数) */
    private Integer charCount;

    /** 分片类型: text / table (表格感知分块标记) */
    private String chunkType;
}
