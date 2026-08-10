package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档分片实体, 对应数据库 kb_doc_chunk 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离.
 * <p>业务约束: Python ingest 生成分片后, 通过 kb_sync 响应回传 chunk 元数据 Java 落库; 仅存 chunk 头 + 尾 content_head + content_tail, 中间占位符省略, 全量文本通过向量库 chunk_id 回查(减少 DB 冗余); char_count 记录原始全长供前端计算省略字数.
 * <p>关联约束: UNIQUE(tenant_id, doc_id, chunk_index), 同一文档内分片序号不可重复; chunk_id 格式 {doc_id}_{chunk_index} 与向量库 metadata 对齐, 支持溯源.
 */
@Data
@TableName("kb_doc_chunk")
public class KbDocChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 所属主文档 id, 指向 knowledge_doc.id; 文档删除(逻辑删)时, 关联分片可保留历史(或级联物理删, 看配置). */
    private Long docId;

    /** 分片唯一标识(格式: {docId}_{chunkIndex}, 如 123_7); 与 Python 向量库 metadata.chunk_id 完全对齐, Agent 检索命中后通过此值回查关联文档. */
    private String chunkId;

    /** 分片序号(文档内从 0 递增, 连续不间断); UNIQUE(docId, chunkIndex) 保证不重复; 重建分片时先按 docId 物理 DELETE 旧分片再 INSERT 新分片. */
    private Integer chunkIndex;

    /** 分片头部文本(前 2*overlap 字符, overlap 默认为 chunk_size 的 20%); 管理员分片预览页展示用, 快速判断分片内容是否合理. */
    private String contentHead;

    /** 分片尾部文本(后 2*overlap 字符); 小分片(char_count < 4*overlap)时与 contentHead 重叠甚至完全相同, 此时 contentTail 可为 NULL. */
    private String contentTail;

    /** 分片原始全量字符数(head+tail 截断前的真实长度, 不含格式标记); 前端展示占位符 "…【省略 N 字】…" 时, N = charCount - len(contentHead) - len(contentTail). */
    private Integer charCount;

    /** 分片类型(枚举字符串: text 普通文本块 / table 表格感知块); table 类型由 Python 分块器识别(Markdown table/HTML table), 检索时表格块权重单独加权. */
    private String chunkType;

    /** 分片创建时间(Asia/Shanghai 时区, Python ingest 成功回传时写入); 与 knowledge_doc.updatedAt 对比可判断分片是否过期需重建. */
    private LocalDateTime createdAt;
}
