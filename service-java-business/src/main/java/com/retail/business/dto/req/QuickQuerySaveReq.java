package com.retail.business.dto.req;

import lombok.Data;

/**
 * 快捷提问保存请求 (个人级).
 * <p>个人快捷提问 isPublic=false, userId 从 LoginUserHolder 自动取, 不由前端传入.
 */
@Data
public class QuickQuerySaveReq {

    /** 快捷提问文本: 用户输入的常用问法 (如 "看下昨天销量") */
    private String shortcutText;

    /** 规范化 query: 绑定的 canonical_query (如 "查询昨日销量") */
    private String canonicalQuery;

    /** 业务场景: order_query/sales_analysis/... (可空) */
    private String scenario;
}
