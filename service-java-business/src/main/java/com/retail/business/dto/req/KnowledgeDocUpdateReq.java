package com.retail.business.dto.req;

import lombok.Data;

import java.time.LocalDate;

/**
 * 知识文档更新请求 (部分更新, 仅允许修改内容/标题/有效期等, domain 不可改).
 * <p>已发布文档更新后需重新发布 (version+1) 才会同步到 Python.
 */
@Data
public class KnowledgeDocUpdateReq {

    /** 文档标题 */
    private String title;

    /** 可见角色ID: NULL=全员可见 */
    private Long roleId;

    /** 门店范围 */
    private Long storeId;

    /** 生效时间 */
    private LocalDate validFrom;

    /** 失效时间 */
    private LocalDate validUntil;

    /** 文档正文 (更新后重写文件, 刷新预览) */
    private String content;
}
