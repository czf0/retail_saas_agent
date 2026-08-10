package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识库文档列表行项(运营知识管理列表 20/页;Agent 检索命中结果摘要);精简字段不含全文正文附件,仅含 contentPreview 前 200 字符预览.
 * <p>点击行进入详情查 KnowledgeDocResp(含全文正文 + 版本历史 + 附件).
 */
@Data
public class KnowledgeDocListItemResp {

    private Long id;
    /** 文档标题(列表页展示标题). */
    private String title;
    /** 知识域:1=SOP(操作流程) 2=FAQ(常见问题) 3=POLICY(制度政策) 4=PRODUCT(商品资料);见 KnowledgeDomainEnum. */
    private Integer domain;
    /** 可见角色外键(sys_role.id);NULL = 全员可见. */
    private Long roleId;
    /** 可见门店外键(sys_store.id);NULL = 全部门店可见. */
    private Long storeId;
    /** 文档状态:1=DRAFT(草稿) 2=PUBLISHED(已发布) 3=ARCHIVED(归档);见 DocStatusEnum. */
    private Integer status;
    /** 失效截止日期(列表页"有效期至";前端可标红过期文档). */
    private LocalDate validUntil;
    /** 当前文档版本号(列表页展示;编辑历史对比用). */
    private Integer currentVersion;
    /** 正文 Markdown 预览(列表页截断前 200 字符;点击详情查全文). */
    private String contentPreview;
    /** 文档来源类型:1=MANUAL(手动录入) 2=FILE_UPLOAD(文件上传) 3=SYSTEM(系统生成);见 KbSourceTypeEnum. */
    private Integer sourceType;
    /** 创建人账号(sys_user.username;列表展示创建者). */
    private String createBy;
    /** 创建时间(默认按此倒序). */
    private LocalDateTime createdAt;
    /** 最近更新时间(列表页"最后更新"列). */
    private LocalDateTime updatedAt;
}
