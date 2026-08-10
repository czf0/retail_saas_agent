package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.KnowledgeDocCreateReq;
import com.retail.business.dto.req.KnowledgeDocUpdateReq;
import com.retail.business.dto.resp.KbChunkItemResp;
import com.retail.business.dto.resp.KnowledgeDocListItemResp;
import com.retail.business.dto.resp.KnowledgeDocResp;
import com.retail.business.service.KnowledgeDocService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档管理接口.
 * <p>路由前缀 /api/v1/kb/docs.knowledge_doc 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("kb:manage") 注解(AOP),仅知识库管理员可操作;
 * 文档生命周期:创建草稿 → 发布(同步 Python 向量库) → 失效(移除索引) → 删除(逻辑删除).
 * <p>注意:/upload,/rebuild,/{docId}/publish,/{docId}/expire,/{docId}/chunks 为字面量路径,
 * 须在 /{docId} 之前注册以保证优先匹配.
 */
@RestController
@RequestMapping("/api/v1/kb/docs")
public class KnowledgeDocController {

    private final KnowledgeDocService knowledgeDocService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public KnowledgeDocController(KnowledgeDocService knowledgeDocService) {
        this.knowledgeDocService = knowledgeDocService;
    }

    /** 创建知识文档 (草稿状态, 不同步 Python) */
    @PostMapping
    @SaCheckPermission("kb:manage")
    public R<KnowledgeDocResp> create(@RequestBody KnowledgeDocCreateReq req) {
        return R.ok(knowledgeDocService.createDraft(req));
    }

    /**
     * 批量上传文件建草稿 (D2 文件上传管控).
     * <p>每文件转发 Python 解析为文本 → 落盘 + 生成 preview + 建草稿 (sourceType=upload).
     * 大小/类型/数量限制由 Service 层校验 (与 application.yml kb.upload 配置 + Python 端双重校验对齐).
     */
    @PostMapping("/upload")
    @SaCheckPermission("kb:manage")
    public R<List<KnowledgeDocResp>> upload(@RequestParam("files") MultipartFile[] files,
                                            @RequestParam("domain") String domain,
                                            @RequestParam(value = "roleId", required = false) Long roleId) {
        return R.ok(knowledgeDocService.upload(files, domain, roleId));
    }

    /**
     * 查询文档分片列表 (D1 chunk 可见性).
     * <p>供管理员查看某文档被分块后的 chunk 明细 (chunkIndex / chunkType / 头尾预览 / charCount).
     */
    @GetMapping("/{docId:\\d+}/chunks")
    @SaCheckPermission("kb:manage")
    public R<List<KbChunkItemResp>> chunks(@PathVariable Long docId) {
        return R.ok(knowledgeDocService.listChunks(docId));
    }

    /** 修改知识文档 (仅内容/标题/有效期等, 不触发 Python 同步) */
    @PutMapping("/{docId:\\d+}")
    @SaCheckPermission("kb:manage")
    public R<KnowledgeDocResp> update(@PathVariable Long docId,
                                      @RequestBody KnowledgeDocUpdateReq req) {
        return R.ok(knowledgeDocService.update(docId, req));
    }

    /**
     * 发布知识文档: status→published, version+1, 同步到 Python 向量库.
     * 已发布文档重新发布会增量更新 Python 索引.
     */
    @PostMapping("/{docId:\\d+}/publish")
    @SaCheckPermission("kb:manage")
    public R<KnowledgeDocResp> publish(@PathVariable Long docId) {
        return R.ok(knowledgeDocService.publish(docId));
    }

    /** 失效知识文档: status→expired, 通知 Python 从向量库移除 */
    @PostMapping("/{docId:\\d+}/expire")
    @SaCheckPermission("kb:manage")
    public R<KnowledgeDocResp> expire(@PathVariable Long docId) {
        return R.ok(knowledgeDocService.expire(docId));
    }

    /** 删除知识文档 (逻辑删除 + 通知 Python 移除索引) */
    @DeleteMapping("/{docId:\\d+}")
    @SaCheckPermission("kb:manage")
    public R<Boolean> delete(@PathVariable Long docId) {
        return R.ok(knowledgeDocService.delete(docId));
    }

    /** 知识文档详情 */
    @GetMapping("/{docId:\\d+}")
    @SaCheckPermission("kb:manage")
    public R<KnowledgeDocResp> detail(@PathVariable Long docId) {
        return R.ok(knowledgeDocService.getDetail(docId));
    }

    /** 分页查询知识文档 (支持 status/domain/keyword 过滤) */
    @GetMapping
    @SaCheckPermission("kb:manage")
    public R<PageResp<KnowledgeDocListItemResp>> list(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String domain,
                                                      @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeDocService.list(status, domain, keyword));
    }

    /**
     * 全量重建 Python 索引 (运维兜底).
     * <p>推送当前租户全部 published 且未过期文档到 Python, 触发 full_rebuild 事件.
     * 适用场景: Python 索引丢失 / 首次部署 / 大规模口径变更后对齐.
     * 返回推送到 Python 的文档数量.
     */
    @PostMapping("/rebuild")
    @SaCheckPermission("kb:manage")
    public R<Integer> rebuild() {
        return R.ok(knowledgeDocService.fullRebuild());
    }
}
