package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.KbDocStatus;
import com.retail.business.enums.KbDomain;
import com.retail.business.enums.KbSourceType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识文档主表实体, 对应数据库 knowledge_doc 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(知识库为租户全局, 按 roleId/storeId 做可见范围控制).
 * <p>业务约束: Java 为 SSOT(单一数据源), Python 为索引消费者; 仅 status=PUBLISHED 且 validUntil 未过期的文档同步到 Python 向量库被检索; 文档管理仅开放管理员 @SaCheckPermission("kb:manage"), 信任上传人, 省去审批流.
 * <p>可见范围控制: roleId = NULL 全员可见, 非空仅该角色可见; storeId = NULL 全局可见, 非空仅该门店可见(C3 单角色绑定).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;
        private Long tenantId;

    /** 文档标题(写入 Python 向量库 metadata.title, Agent 检索命中后溯源展示给用户; 建议包含关键词便于 BM25 召回). */
    private String title;

    /** 业务域(KbDomain 枚举本体: 1=ORDER 订单, 2=INVENTORY 库存, 3=SALES 销售, 4=PROMOTION 促销, 5=MEMBER 会员, 6=SOP SOP, 7=CATEGORY_TREE 分类树, 8=PRODUCT_CATALOG 商品目录, 9=STORE_LIST 门店列表); NULL=跨域通用, Python 检索时可按 domain 过滤缩小范围. */
    private KbDomain domain;

    /** 可见角色 id(可见范围控制 C3); NULL=全员可见; 非空=仅该 sys_role 角色可见(单角色绑定, 多角色可见需拆多个文档 or 后续扩展 JSON 数组). */
    private Long roleId;

    /** 门店范围(可见范围控制 C3); NULL=全局可见(所有门店); 非空=仅该门店店长/店员可见(如 "XX 店 6 月促销活动细则" 店长级文档). */
    private Long storeId;

    /** 文档状态(KbDocStatus 枚举本体: 1=DRAFT 草稿, 2=PUBLISHED 发布, 3=EXPIRED 过期, 4=ARCHIVED 归档); 仅 PUBLISHED 且未过期文档同步 Python 向量库; 过期/归档文档自动从向量库移除. */
    private KbDocStatus status;

    /** 生效时间(Asia/Shanghai 时区 LocalDate, 含当天); 促销政策可定时生效, NULL=立即生效(创建即发布, 配合 status=PUBLISHED). */
    private LocalDate validFrom;

    /** 失效时间(Asia/Shanghai 时区 LocalDate, 含当天); 检索时自动过滤过期文档(C5 要求); NULL=长期有效(无失效时间, 需手动归档). */
    private LocalDate validUntil;

    /** 当前版本号(文档级版本 D4); 每次发布 +1, 回滚可降级; 配合 kb_doc_chunk.version 做分片级版本(保留扩展). */
    private Integer currentVersion;

    /** 文档预览文本(前 200 字, 纯文本, 去除格式); 列表页/详情页快速预览用, 不存全量原文减少冗余(全量存 filePath 指向磁盘). */
    private String contentPreview;

    /** 原文文件落盘绝对路径(规则: data/kb_files/{tenantId}/{docId}.{ext}); 原文 SSOT, Python ingest 同步时读取此文件分片, 文件损坏/丢失可重新上传覆盖. */
    private String filePath;

    /** 来源类型(KbSourceType 枚举本体: 1=MANUAL 手动录入, 2=UPLOAD 文件上传, 3=GENERATED 系统生成, 4=IMPORT 批量导入); 来源标记便于报表统计文档构成 + 后续溯源追责. */
    private KbSourceType sourceType;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        private Integer deleted = 0;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
