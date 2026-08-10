package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识库文档详情页响应(Agent RAG 检索命中后返回给前端展示;运营"知识管理"详情编辑共用);包含全文 contentPreview + 附件 filePath + 生效/失效期 + 可见范围.
 * <p>Controller: GET /api/v1/knowledge/docs/{id:\\d+};{id} 正则守卫;到期(validUntil < 今天)前台不展示.
 */
@Data
public class KnowledgeDocResp {

    private Long id;
    /** 租户外键(多租户知识库隔离;跨租户共享 = NULL 平台级公共文档). */
    private Long tenantId;
    /** 文档标题(RAG 检索匹配度高时 Agent 直接引用标题作答). */
    private String title;
    /** 知识域:1=SOP(操作流程) 2=FAQ(常见问题) 3=POLICY(制度政策) 4=PRODUCT(商品资料);见 KnowledgeDomainEnum. */
    private Integer domain;
    /** 可见角色外键(sys_role.id);NULL = 全员可见;非 NULL = 仅该角色(如店长/收银员)知识助手检索能命中. */
    private Long roleId;
    /** 可见门店外键(sys_store.id);NULL = 全部门店;非 NULL = 仅该门店员工调用 Agent 能命中. */
    private Long storeId;
    /** 文档状态:1=DRAFT(草稿) 2=PUBLISHED(已发布) 3=ARCHIVED(归档);见 DocStatusEnum.只有 PUBLISHED 参与 RAG 检索. */
    private Integer status;
    /** 生效起始日期(包含;此日期前即使 PUBLISHED 也不参与检索命中). */
    private LocalDate validFrom;
    /** 失效截止日期(包含;过期自动归档不参与检索,到期不提醒需运营主动续期). */
    private LocalDate validUntil;
    /** 当前文档版本号(单调递增 int;每次编辑 +1;回滚时取 previousVersion 内容覆盖). */
    private Integer currentVersion;
    /** 正文 Markdown 预览(截断前 500 字符;详情页完整正文单独走 content 接口按段返回). */
    private String contentPreview;
    /** 原始附件文件路径(PDF/DOCX/XLSX/PPTX 等;NULL = 纯在线 Markdown 录入无附件). */
    private String filePath;
    /** 文档来源类型:1=MANUAL(手动录入) 2=FILE_UPLOAD(文件上传) 3=SYSTEM(系统生成);见 KbSourceTypeEnum. */
    private Integer sourceType;
    /** 创建人账号(sys_user.username;NULL = 系统自动生成). */
    private String createBy;
    /** 最近一次编辑人账号(sys_user.username;NULL = 系统自动更新). */
    private String updateBy;
    /** 创建时间(按此倒序列表). */
    private LocalDateTime createdAt;
    /** 最近更新时间(版本号每次 +1 时同步刷新). */
    private LocalDateTime updatedAt;
}
