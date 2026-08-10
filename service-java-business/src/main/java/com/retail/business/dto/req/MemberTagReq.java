package com.retail.business.dto.req;

import lombok.Data;

/**
 * 创建/更新会员标签请求.
 * <p>创建时 tagName 必填;更新时全部字段可空(部分更新).
 */
@Data
public class MemberTagReq {

    /** 标签名称(创建时必填,租户内唯一) */
    private String tagName;

    /** 展示色,如 #FF6B6B(可空) */
    private String tagColor;

    /** 标签描述(可空) */
    private String description;
}
