package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.KnowledgeDocConvert;
import com.retail.business.dto.req.KnowledgeDocCreateReq;
import com.retail.business.dto.req.KnowledgeDocUpdateReq;
import com.retail.business.dto.resp.KbChunkItemResp;
import com.retail.business.dto.resp.KnowledgeDocListItemResp;
import com.retail.business.dto.resp.KnowledgeDocResp;
import com.retail.business.entity.KbDocChunk;
import com.retail.business.entity.KnowledgeDoc;
import com.retail.business.enums.KbDocStatus;
import com.retail.business.enums.KbSourceType;
import com.retail.business.mapper.KbDocChunkMapper;
import com.retail.business.mapper.KnowledgeDocMapper;
import com.retail.business.service.KnowledgeDocService;
import com.retail.core.client.KbFileParseClient;
import com.retail.core.client.KnowledgeSyncNotifier;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.dto.kb.KnowledgeSyncEvent;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.tenant.TenantContext;
import com.retail.core.trace.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识文档服务实现 (Java SSOT + Python 索引同步).
 * <p>
 * 设计要点:
 * - tenant_id 由 MyBatis-Plus 多租户拦截器自动注入 (knowledge_doc 不在 ignore-tables);
 * - 审计字段 (createBy/updateBy) 由 MetaObjectHandler 自动填充;
 * - 逻辑删除由 BaseServiceImpl.removeById 填充 deleteAt/deleteBy;
 * - 发布/失效/删除时通过 KnowledgeSyncNotifier 通知 Python 增量更新索引;
 * - 通知失败不回滚主事务 (catch + warn), Python 侧定时全量校对兜底.
 * <p>
 * 与 Python kb_sync 的协议:
 * - doc_upsert: 发布时推送文档到 Python (ingest 按 doc_id 去重);
 * - doc_delete: 失效/删除时通知 Python 移除索引 (按 doc_id 清理).
 */
@Slf4j
@Service
public class KnowledgeDocServiceImpl extends BaseServiceImpl<KnowledgeDocMapper, KnowledgeDoc>
        implements KnowledgeDocService {

    /** 知识文档原文落盘根目录 (取自 kb.file-dir 配置, 默认 data/kb_files) */
    @Value("${kb.file-dir:data/kb_files}")
    private String kbFileDir;
    /** 允许上传的文件扩展名白名单 (取自 kb.upload.allowed-types, 逗号分隔) */
    @Value("${kb.upload.allowed-types:txt,md,pdf,docx}")
    private String allowedTypes;
    /** 单次上传文件数上限 (取自 kb.upload.max-files) */
    @Value("${kb.upload.max-files:5}")
    private int maxFiles;
    /** content_preview 最大长度 (前 N 字符, 列表/详情展示用) */
    private static final int PREVIEW_MAX_LENGTH = 50;

    private final KnowledgeDocConvert knowledgeDocConvert;
    private final KnowledgeSyncNotifier syncNotifier;
    private final KbFileParseClient fileParseClient;
    private final KbDocChunkMapper kbDocChunkMapper;

    public KnowledgeDocServiceImpl(KnowledgeDocConvert knowledgeDocConvert,
                                   KnowledgeSyncNotifier syncNotifier,
                                   KbFileParseClient fileParseClient,
                                   KbDocChunkMapper kbDocChunkMapper) {
        this.knowledgeDocConvert = knowledgeDocConvert;
        this.syncNotifier = syncNotifier;
        this.fileParseClient = fileParseClient;
        this.kbDocChunkMapper = kbDocChunkMapper;
    }

    @Override
    public KnowledgeDocResp createDraft(KnowledgeDocCreateReq req) {
        validateCreateReq(req);
        KnowledgeDoc entity = knowledgeDocConvert.toEntity(req);
        // 草稿状态: 不同步到 Python 向量库
        entity.setStatus(KbDocStatus.DRAFT);
        entity.setCurrentVersion(0);
        entity.setSourceType(EnumUtil.fromCode(KbSourceType.class, req.getSourceType()));
        // content 不入 DB, 仅生成 preview; 全量原文落盘 (需先 save 拿自增 ID 构造 file_path)
        entity.setContentPreview(generatePreview(req.getContent()));
        save(entity);
        // 拿到自增 ID 后写文件, 回填 file_path
        String filePath = writeContentToFile(entity.getId(), req.getContent());
        entity.setFilePath(filePath);
        updateById(entity);
        log.info("创建知识文档草稿 docId={} title={} domain={} roleId={} filePath={}",
                entity.getId(), entity.getTitle(), entity.getDomain(),
                entity.getRoleId(), filePath);
        return knowledgeDocConvert.toResp(entity);
    }

    @Override
    public KnowledgeDocResp update(Long docId, KnowledgeDocUpdateReq req) {
        KnowledgeDoc entity = requireDoc(docId);
        // 部分更新: 仅允许修改以下字段 (domain/status 不可改)
        if (StrUtil.isNotBlank(req.getTitle())) {
            entity.setTitle(req.getTitle());
        }
        // roleId: 非空时覆盖 (null=全员可见, 需通过显式传 0 或新增接口置空, 与原 roleScope 行为一致)
        if (req.getRoleId() != null) {
            entity.setRoleId(req.getRoleId());
        }
        if (req.getStoreId() != null) {
            entity.setStoreId(req.getStoreId());
        }
        if (req.getValidFrom() != null) {
            entity.setValidFrom(req.getValidFrom());
        }
        if (req.getValidUntil() != null) {
            entity.setValidUntil(req.getValidUntil());
        }
        if (StrUtil.isNotBlank(req.getContent())) {
            // 内容变更: 重写文件 + 刷新 preview
            writeContentToFile(entity.getId(), req.getContent());
            entity.setContentPreview(generatePreview(req.getContent()));
        }
        updateById(entity);
        log.info("更新知识文档 docId={} title={} roleId={} storeId={}",
                docId, entity.getTitle(), entity.getRoleId(), entity.getStoreId());
        return knowledgeDocConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocResp publish(Long docId) {
        KnowledgeDoc entity = requireDoc(docId);
        // 版本 +1 (D4 文档级版本), 状态置 published
        entity.setCurrentVersion((entity.getCurrentVersion() == null ? 0 : entity.getCurrentVersion()) + 1);
        entity.setStatus(KbDocStatus.PUBLISHED);
        updateById(entity);

        // 同步到 Python 向量库 (doc_upsert)
        notifyPythonUpsert(entity);

        log.info("发布知识文档 docId={} title={} currentVersion={} domain={} roleId={}",
                docId, entity.getTitle(), entity.getCurrentVersion(),
                entity.getDomain(), entity.getRoleId());
        return knowledgeDocConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocResp expire(Long docId) {
        KnowledgeDoc entity = requireDoc(docId);
        entity.setStatus(KbDocStatus.EXPIRED);
        updateById(entity);

        // 通知 Python 从向量库移除 (doc_delete)
        notifyPythonDelete(entity);

        log.warn("失效知识文档 docId={} title={}（已通知 Python 移除索引）", docId, entity.getTitle());
        return knowledgeDocConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(Long docId) {
        KnowledgeDoc entity = requireDoc(docId);
        // 逻辑删除 (BaseServiceImpl.removeById 填充 deleteAt/deleteBy)
        boolean removed = removeById(docId);
        if (removed) {
            // 通知 Python 从向量库移除 (doc_delete)
            notifyPythonDelete(entity);
            log.warn("删除知识文档 docId={} title={}（逻辑删除，已通知 Python 移除索引）",
                    docId, entity.getTitle());
        } else {
            log.warn("删除知识文档失败 docId={} 原因=removeById 返回 false", docId);
        }
        return removed;
    }

    @Override
    public KnowledgeDocResp getDetail(Long docId) {
        return knowledgeDocConvert.toResp(requireDoc(docId));
    }

    @Override
    public PageResp<KnowledgeDocListItemResp> list(String status, String domain, String keyword) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(KnowledgeDoc::getStatus, status);
        }
        if (StrUtil.isNotBlank(domain)) {
            wrapper.eq(KnowledgeDoc::getDomain, domain);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(KnowledgeDoc::getTitle, keyword);
        }
        wrapper.orderByDesc(KnowledgeDoc::getUpdatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal(Agent 工具路径手动注入);
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<KnowledgeDoc> pageObj = PageContextHolder.get();
        IPage<KnowledgeDoc> result = page(pageObj, wrapper);
        List<KnowledgeDocListItemResp> items = knowledgeDocConvert.toListItemList(result.getRecords());
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer fullRebuild() {
        // 查询当前租户全部已发布且未过期的文档 (tenant_id 由拦截器自动注入, valid_until 过期的不重建)
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getStatus, KbDocStatus.PUBLISHED);
        // valid_until 为空 (永久有效) 或 >= today (未过期) 才纳入重建
        wrapper.and(q -> q.isNull(KnowledgeDoc::getValidUntil)
                .or().ge(KnowledgeDoc::getValidUntil, today));
        List<KnowledgeDoc> docs = list(wrapper);

        // 构造 DocItem 列表 (与 doc_upsert 同转换逻辑, 保证 Python 侧字段对齐)
        List<KnowledgeSyncEvent.DocItem> items = new ArrayList<>(docs.size());
        for (KnowledgeDoc d : docs) {
            items.add(toDocItem(d));
        }

        // 解析租户 ID: 优先取首文档 tenantId (查询已带租户拦截, 同属一租户), 回退 TenantContext
        String tenantId = docs.isEmpty() ? currentTenantId() : resolveTenantId(docs.get(0));
        KnowledgeSyncEvent event = KnowledgeSyncEvent.fullRebuild(
                tenantId, TraceUtil.getTraceId(), items);
        Map<String, Object> respData = syncNotifier.notify(event);

        // 全量重建后同步落库分片 (与 doc_upsert 一致): Python 返回该租户全量 chunk 元信息,
        // 按 chunk_id 前缀解析 doc_id 分组落库 kb_doc_chunk, 保证管理端「分片明细」可见.
        // 修复: 原实现只 notify 不落库, 导致 Python 索引已重建但 kb_doc_chunk 为空 (种子文档场景).
        if (respData != null && respData.get("chunks") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chunks = (List<Map<String, Object>>) respData.get("chunks");
            persistRebuildChunks(chunks);
        }

        log.info("kb_full_rebuild_triggered tenant={} published_docs={}", tenantId, items.size());
        return items.size();
    }

    /**
     * 全量重建后按文档分组落库分片.
     * <p>Python full_rebuild 复用 doc_upsert, 返回的 chunks 为全租户扁平列表 (含 chunk_id 形如
     * {@code {doc_id}_{chunk_index}}), 此处按 chunk_id 前缀解析 doc_id, 分组后逐文档调用
     * {@link #persistChunks} (其内部按 doc_id 先删旧再插新, 幂等).
     *
     * @param chunks Python 回传的全量分片列表 (List&lt;Map&gt;, 含 chunk_id/chunk_index/...)
     */
    private void persistRebuildChunks(List<Map<String, Object>> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        // doc_id -> 该文档的 chunk 列表, 保持原 chunk 顺序
        Map<Long, List<Map<String, Object>>> byDoc = new LinkedHashMap<>();
        for (Map<String, Object> c : chunks) {
            Long docId = parseDocIdFromChunkId(asString(c.get("chunk_id")));
            if (docId == null) {
                continue;
            }
            byDoc.computeIfAbsent(docId, k -> new ArrayList<>()).add(c);
        }
        int total = 0;
        for (Map.Entry<Long, List<Map<String, Object>>> e : byDoc.entrySet()) {
            persistChunks(e.getKey(), e.getValue());
            total += e.getValue().size();
        }
        log.info("kb_full_rebuild_chunks_persisted docs={} total_chunks={}", byDoc.size(), total);
    }

    /** 从 chunk_id ({doc_id}_{chunk_index}) 解析 doc_id, 解析失败返回 null */
    private static Long parseDocIdFromChunkId(String chunkId) {
        if (chunkId == null || chunkId.isEmpty()) {
            return null;
        }
        int idx = chunkId.lastIndexOf('_');
        if (idx <= 0) {
            return null;
        }
        try {
            return Long.parseLong(chunkId.substring(0, idx));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- 内部方法 ----

    /** 按主键查询, 不存在抛 ParamException (与 PointsServiceImpl/RefundServiceImpl 的 requireXxx 同模式) */
    private KnowledgeDoc requireDoc(Long docId) {
        if (docId == null) {
            throw new ParamException("文档ID不能为空");
        }
        KnowledgeDoc entity = getById(docId);
        if (entity == null) {
            // 不拼接 docId, 避免向用户泄漏内部主键 (技术细节仅入日志)
            throw new ParamException("知识文档不存在");
        }
        return entity;
    }

    /** 创建请求校验: 必填字段 */
    private void validateCreateReq(KnowledgeDocCreateReq req) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw new ParamException("标题不能为空");
        }
        if (req.getDomain() == null) {
            throw new ParamException("业务域不能为空");
        }
        if (StrUtil.isBlank(req.getContent())) {
            throw new ParamException("内容不能为空");
        }
        // roleId: null=全员可见, 非空时不做合法性校验 (由前端从 sys_role 列表选择, Service 信任前端)
        // 有效期校验: validFrom 不能晚于 validUntil
        if (req.getValidFrom() != null && req.getValidUntil() != null
                && req.getValidFrom().isAfter(req.getValidUntil())) {
            throw new ParamException("生效时间不能晚于失效时间");
        }
    }

    /**
     * 通知 Python 增量入库 (doc_upsert) 并落库 chunks.
     * <p>实体 → DocItem 转换: Long→String, LocalDate→String, 与 Python 侧 KbSyncDocItem 对齐.
     * D1.2: 通知升级为请求-响应, Python 返回 chunks 元信息, Service 落库 kb_doc_chunk.
     * 通知失败不抛异常 (Notifier 内部 catch), Python 侧定时全量校对兜底; chunks 不落库下次 publish 补.
     */
    private void notifyPythonUpsert(KnowledgeDoc entity) {
        KnowledgeSyncEvent.DocItem item = toDocItem(entity);
        String tenantId = resolveTenantId(entity);
        KnowledgeSyncEvent event = KnowledgeSyncEvent.docUpsert(
                tenantId, TraceUtil.getTraceId(), Collections.singletonList(item));
        // D1.2: 获取 Python 响应 data (含 chunks), 落库 kb_doc_chunk
        Map<String, Object> respData = syncNotifier.notify(event);
        if (respData != null) {
            Object chunksObj = respData.get("chunks");
            if (chunksObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> chunks = (List<Map<String, Object>>) chunksObj;
                persistChunks(entity.getId(), chunks);
            }
        }
    }

    /** 通知 Python 从索引移除 (doc_delete) */
    private void notifyPythonDelete(KnowledgeDoc entity) {
        String tenantId = resolveTenantId(entity);
        String docId = String.valueOf(entity.getId());
        KnowledgeSyncEvent event = KnowledgeSyncEvent.docDelete(
                tenantId, TraceUtil.getTraceId(), Collections.singletonList(docId));
        syncNotifier.notify(event);
    }

    /**
     * 解析租户 ID (字符串, 供 Python 侧 collection 隔离).
     * <p>优先取实体 tenantId, 回退 TenantContext (创建场景实体 tenantId 由拦截器在 INSERT 时注入, 此处可能为 null).
     */
    private String resolveTenantId(KnowledgeDoc entity) {
        if (entity.getTenantId() != null) {
            return String.valueOf(entity.getTenantId());
        }
        return currentTenantId();
    }

    /**
     * 取当前租户 ID (字符串), 供无具体实体的场景 (如 full_rebuild 空集) 解析租户.
     * <p>TenantContext 为空 (如内部任务无登录态) 时回退 "default", 与 Python 侧 default collection 对齐.
     */
    private String currentTenantId() {
        String ctxTenant = TenantContext.getTenantId();
        return ctxTenant != null ? ctxTenant : "default";
    }

    /** 实体 → Python 同步 DocItem (字段类型转换: Long→String, LocalDate→String; content 从 file_path 读取) */
    private KnowledgeSyncEvent.DocItem toDocItem(KnowledgeDoc entity) {
        KnowledgeSyncEvent.DocItem item = new KnowledgeSyncEvent.DocItem();
        item.setDocId(String.valueOf(entity.getId()));
        item.setTitle(entity.getTitle() != null ? entity.getTitle() : "");
        // 全量原文从 file_path 读取 (DB 仅存 preview, 不冗余全量)
        item.setContent(readContentFromFile(entity.getFilePath()));
        item.setDomain(entity.getDomain() != null ? String.valueOf(entity.getDomain().getCode()) : "");
        item.setRoleId(entity.getRoleId() != null ? String.valueOf(entity.getRoleId()) : "");
        item.setStoreId(entity.getStoreId() != null ? String.valueOf(entity.getStoreId()) : "");
        item.setValidUntil(entity.getValidUntil() != null ? entity.getValidUntil().toString() : "");
        item.setVersion(entity.getCurrentVersion() != null ? entity.getCurrentVersion() : 1);
        return item;
    }

    // ---- 文件 I/O 工具方法 ----

    /** 生成 content_preview: 截取前 PREVIEW_MAX_LENGTH 字符 */
    private static String generatePreview(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.length() <= PREVIEW_MAX_LENGTH
                ? content
                : content.substring(0, PREVIEW_MAX_LENGTH);
    }

    /**
     * 将文档原文写入落盘文件.
     * <p>路径: {kbFileDir}/{tenantId}/{docId}.txt
     * 目录不存在自动创建; 写入失败抛 ParamException (不阻断主流程, 文件缺失时 toDocItem 降级返回空).
     *
     * @param docId   文档 ID (自增主键)
     * @param content 文档原文
     * @return 相对文件路径 (供 entity.filePath 存储)
     */
    private String writeContentToFile(Long docId, String content) {
        if (docId == null || content == null) {
            return null;
        }
        String tenantId = currentTenantId();
        String relativePath = kbFileDir + "/" + tenantId + "/" + docId + ".txt";
        Path fullPath = Paths.get(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, content.getBytes(StandardCharsets.UTF_8));
            return relativePath;
        } catch (IOException e) {
            log.error("kb_file_write_failed docId={} path={} error={}", docId, relativePath, e.getMessage());
            throw new ParamException("文档原文保存失败");
        }
    }

    /**
     * 从落盘文件读取文档原文 (供 Python 同步时获取全量 content).
     * <p>文件不存在时返回空字符串 (降级, 不阻断同步流程; Python 侧按空内容跳过).
     *
     * @param filePath 相对文件路径 (entity.filePath)
     * @return 文档原文, 文件缺失时返回 ""
     */
    private String readContentFromFile(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return "";
        }
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            log.warn("kb_file_not_found path={} (降级返回空内容)", filePath);
            return "";
        } catch (IOException e) {
            log.error("kb_file_read_failed path={} error={}", filePath, e.getMessage());
            return "";
        }
    }

    // ---- D2 文件上传管控 ----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<KnowledgeDocResp> upload(MultipartFile[] files, String domain, Long roleId) {
        if (files == null || files.length == 0) {
            throw new ParamException("请至少上传一个文件");
        }
        if (StrUtil.isBlank(domain)) {
            throw new ParamException("业务域不能为空");
        }
        if (files.length > maxFiles) {
            throw new ParamException("单次最多上传 " + maxFiles + " 个文件");
        }
        // 解析允许的扩展名白名单 (小写, 去空白)
        Set<String> allowed = new HashSet<>();
        for (String t : allowedTypes.split(",")) {
            if (StrUtil.isNotBlank(t)) {
                allowed.add(t.trim().toLowerCase());
            }
        }

        List<KnowledgeDocResp> created = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = file.getOriginalFilename();
            // 校验扩展名 (双重校验, 与 Python 端白名单对齐)
            String ext = getExtension(filename);
            if (!allowed.contains(ext)) {
                log.warn("kb_upload_skip_unsupported_type filename={} ext={}", filename, ext);
                throw new ParamException("不支持的文件类型: " + filename + ", 仅支持 " + allowedTypes);
            }
            try {
                // 转发到 Python 解析为文本
                Map<String, Object> parseResult = fileParseClient.parseFile(filename, file.getBytes());
                if (parseResult == null || !Boolean.TRUE.equals(parseResult.get("ok"))) {
                    // Python 不可用或解析失败: 提示用户 (技术细节仅入日志)
                    String msg = parseResult != null ? String.valueOf(parseResult.get("message")) : "文件解析服务暂不可用";
                    log.warn("kb_upload_parse_failed filename={} msg={}", filename, msg);
                    throw new ParamException("文件「" + filename + "」解析失败: " + msg);
                }
                String text = String.valueOf(parseResult.getOrDefault("text", ""));
                if (StrUtil.isBlank(text)) {
                    throw new ParamException("文件「" + filename + "」解析结果为空 (可能是扫描件 PDF 无文本层)");
                }
                // 建草稿: 标题取文件名去扩展名, sourceType=upload
                KnowledgeDocCreateReq req = new KnowledgeDocCreateReq();
                req.setTitle(stripExtension(filename));
                req.setDomain(Integer.valueOf(domain));
                req.setRoleId(roleId);
                req.setSourceType(KbSourceType.UPLOAD.getCode());
                req.setContent(text);
                created.add(createDraft(req));
            } catch (IOException e) {
                log.error("kb_upload_read_failed filename={} error={}", filename, e.getMessage());
                throw new ParamException("文件「" + filename + "」读取失败");
            }
        }
        log.info("kb_upload_done domain={} roleId={} success={}/{}", domain, roleId, created.size(), files.length);
        return created;
    }

    /** 取小写扩展名 (不含点), 无扩展名返回空串 */
    private static String getExtension(String filename) {
        if (StrUtil.isBlank(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** 去除扩展名的文件名 (用作文档标题默认值) */
    private static String stripExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "未命名文档";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    // ---- D1 chunk 可见性 ----

    @Override
    public List<KbChunkItemResp> listChunks(Long docId) {
        // 校验文档存在 + 租户隔离 (requireDoc 走 getById, 拦截器自动注入 tenant_id 过滤)
        requireDoc(docId);
        LambdaQueryWrapper<KbDocChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocChunk::getDocId, docId);
        wrapper.orderByAsc(KbDocChunk::getChunkIndex);
        List<KbDocChunk> chunks = kbDocChunkMapper.selectList(wrapper);
        List<KbChunkItemResp> result = new ArrayList<>(chunks.size());
        for (KbDocChunk c : chunks) {
            KbChunkItemResp item = new KbChunkItemResp();
            item.setChunkId(c.getChunkId());
            item.setChunkIndex(c.getChunkIndex());
            item.setContentHead(c.getContentHead());
            item.setContentTail(c.getContentTail());
            item.setCharCount(c.getCharCount());
            item.setChunkType(c.getChunkType());
            result.add(item);
        }
        return result;
    }

    /**
     * 将 Python kb_sync 响应回传的 chunk 元数据落库到 kb_doc_chunk (D1 chunk 持久化).
     * <p>publish 时调用: 先按 docId 删旧再插新 (幂等 upsert), 保证重新发布后 chunk 表与向量库一致.
     * 通知失败 (无 chunks 响应) 时不落库, 下次 publish 补; 不阻断主事务.
     *
     * @param docId  文档 ID
     * @param chunks Python 回传的 chunk 列表 (List&lt;Map&gt;, 含 chunk_id/chunk_index/content_head/content_tail/char_count/chunk_type)
     */
    private void persistChunks(Long docId, List<Map<String, Object>> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        // 先删旧 (幂等: 重新发布时清理上一版 chunk, 避免残留)
        LambdaQueryWrapper<KbDocChunk> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(KbDocChunk::getDocId, docId);
        kbDocChunkMapper.delete(delWrapper);
        // 再插新
        for (Map<String, Object> c : chunks) {
            KbDocChunk entity = new KbDocChunk();
            entity.setDocId(docId);
            entity.setChunkId(asString(c.get("chunk_id")));
            entity.setChunkIndex(asInt(c.get("chunk_index")));
            entity.setContentHead(asString(c.get("content_head")));
            entity.setContentTail(asString(c.get("content_tail")));
            entity.setCharCount(asInt(c.get("char_count")));
            entity.setChunkType(asString(c.getOrDefault("chunk_type", "text")));
            kbDocChunkMapper.insert(entity);
        }
        log.info("kb_chunks_persisted docId={} count={}", docId, chunks.size());
    }

    /** 安全类型转换: Object → String (null 安全) */
    private static String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    /** 安全类型转换: Object → Integer (null 安全, Number/String 兼容) */
    private static Integer asInt(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
