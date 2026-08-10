package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.req.KnowledgeDocCreateReq;
import com.retail.business.dto.req.KnowledgeDocUpdateReq;
import com.retail.business.dto.resp.KnowledgeDocListItemResp;
import com.retail.business.dto.resp.KnowledgeDocResp;
import com.retail.business.dto.resp.KbChunkItemResp;
import com.retail.business.entity.KnowledgeDoc;
import com.retail.core.dto.PageResp;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档服务 (Java SSOT, 文档生命周期管理 + Python 索引同步).
 * <p>
 * 负责文档 CRUD + 发布/失效 + 同步通知 Python (评审 C1/C5 + 知识文档管理模块设计 §4.4).
 * 仅 status=published 且 valid_until 未过期的文档同步到 Python 向量库.
 */
public interface KnowledgeDocService extends IService<KnowledgeDoc> {

    /** 创建草稿: status=draft, 不同步到 Python (需调 publish 才同步) */
    KnowledgeDocResp createDraft(KnowledgeDocCreateReq req);

    /** 部分更新: 仅 title/roleId/storeId/validFrom/validUntil/content 可改 (domain 不可改) */
    KnowledgeDocResp update(Long docId, KnowledgeDocUpdateReq req);

    /**
     * 批量上传文件建草稿 (D2 文件上传管控).
     * <p>
     * 每文件: 校验大小/类型 → 转发 Python 解析为文本 → 落盘 file_path + 生成 preview + 建草稿 (sourceType=upload).
     * 标题默认取文件名 (去扩展名), 业务域需前端统一传入 (上传多文件同域).
     * 解析失败的文件跳过并记录, 不影响其他文件.
     *
     * @param files   上传的文件列表 (MultipartFile[])
     * @param domain  业务域 (所有文件共用, 与手动建草稿一致)
     * @param roleId  可见角色ID (null=全员可见)
     * @return 建成功的草稿列表 (解析失败的不在其中)
     */
    List<KnowledgeDocResp> upload(MultipartFile[] files, String domain, Long roleId);

    /**
     * 查询文档分片列表 (D1 chunk 可见性).
     * <p>
     * 供管理员查看某文档被分块后的 chunk 明细 (chunkIndex / chunkType / 头尾预览 / charCount),
     * 分片在 publish 时由 Python ingest 生成并经 kb_sync 响应回传落库.
     *
     * @param docId 文档 ID
     * @return 分片列表 (按 chunkIndex 升序)
     */
    List<KbChunkItemResp> listChunks(Long docId);

    /**
     * 发布文档: status=draft→published, version+1, 同步到 Python 向量库.
     * 已发布文档重新发布会增量更新 Python 索引 (version+1, ingest 按 doc_id 去重).
     */
    KnowledgeDocResp publish(Long docId);

    /**
     * 失效文档: status→expired, 通知 Python 从向量库移除.
     * 用于手动失效 (如政策废止) 或定时任务扫描 valid_until 过期触发.
     */
    KnowledgeDocResp expire(Long docId);

    /**
     * 逻辑删除: 标记 deleted=1, 通知 Python 从向量库移除.
     */
    Boolean delete(Long docId);

    /** 文档详情 */
    KnowledgeDocResp getDetail(Long docId);

    /**
     * 分页查询: 支持 status/domain/keyword 过滤.
     * 租户隔离由 MyBatis-Plus 拦截器自动注入 tenant_id.
     */
    PageResp<KnowledgeDocListItemResp> list(String status, String domain, String keyword);

    /**
     * 全量重建 Python 索引 (运维兜底, 评审 D1 + 知识文档管理模块设计 §4.2).
     * <p>
     * 适用场景: Python 索引丢失 (BM25 持久化损坏) / 首次部署 / 大规模口径变更后对齐.
     * <p>
     * 实现: 查询当前租户全部 status=published 且未过期 (valid_until 为空或 >= 今日) 的文档,
     * 构造 DocItem 列表, 发送 full_rebuild 事件到 Python; Python 侧复用 upsert 逻辑重建索引.
     * <p>
     * 返回: 推送到 Python 的文档数量 (空集也会通知 Python 清缓存).
     */
    Integer fullRebuild();
}
