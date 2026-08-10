package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 知识库文档生命周期状态枚举; code = 1 草稿, 2 已发布, 3 失效, 4 归档.
 * <p>生命周期及与 Python 向量库同步行为:
 * <ul>
 *   <li>DRAFT(1 草稿): 编辑中; 不同步至 Python 向量库; 仅创建者/编辑者在后台可见.</li>
 *   <li>PUBLISHED(2 已发布): 定稿版本; 同步至 Python 向量库(计算 embedding + upsert); Agent RAG 检索可命中.</li>
 *   <li>EXPIRED(3 失效): 超过有效日期或手动作废; 从 Python 向量库移除(向量索引硬删); 不可检索.</li>
 *   <li>ARCHIVED(4 归档): 历史版本归档备查; 不在向量库; 不可直接恢复至 PUBLISHED(需克隆重发布).</li>
 * </ul>
 */
public enum KbDocStatus implements BaseEnum {

    /** 草稿(编辑工作副本); 不同步至 Python 向量库; Agent 不可检索; 编辑会话期间每 30 秒自动保存. */
    DRAFT(1, "草稿"),
    /** 已发布(生效版本); 计算 embedding 后同步至 Python 向量库; Agent RAG 检索时按 KbDomain 过滤命中. */
    PUBLISHED(2, "已发布"),
    /** 失效(作废); 从 Python 向量库索引硬删; Agent 不可检索; MySQL 保留供历史追溯. */
    EXPIRED(3, "失效"),
    /** 归档(历史版本快照); 不在向量库; 不可检索; 不可直接恢复 - 克隆内容至新 DRAFT 后再发布. */
    ARCHIVED(4, "归档");

    @EnumValue
    private final Integer code;
    private final String desc;

    KbDocStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
