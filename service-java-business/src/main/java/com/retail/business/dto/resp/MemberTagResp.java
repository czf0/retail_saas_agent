package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 会员标签详情/列表行项响应(会员标签管理 + 会员详情"标签"区);标签用于人群细分(RFM/自定义打标),支持按标签筛选会员群发券.
 * <p>Controller: GET /api/v1/member-tags/{id:\\d+} 详情 或 GET /api/v1/member-tags 列表.
 */
@Data
public class MemberTagResp {

    private Long id;

    /** 标签显示名(中文,如"高价值会员"/"沉睡30天";前端标签 chip 文本). */
    private String tagName;

    /** 标签主题色(HEX 值,如 #409EFF;前端 chip 背景色). */
    private String tagColor;

    /** 标签说明(运营人员内部备注;前端 tooltip 展示). */
    private String description;

    /** 计算字段(SQL COUNT member_tag_rel 内嵌):当前标签下有效会员数;前端 chip 右侧 "(N)" 后缀. */
    private Long memberCount;
}
