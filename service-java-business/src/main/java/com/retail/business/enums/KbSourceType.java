package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 知识库文档来源类型枚举; code 对齐 knowledge_doc.source_type 列(INT 1 ~ 3).
 * <p>表示文档原始创建方式, 用于审计追溯和编辑器 UI 差异化:
 * <ul>
 *   <li>MANUAL(1 手动录入): 运营在 KB 后台编辑器手动创建文档; 完整 WYSIWYG Markdown 编辑器; 审计日志记录创建者和修改者.</li>
 *   <li>UPLOAD(2 文件上传): 通过文件上传批量导入(.md/.docx/.pdf/.txt; 解析器提取文本 + 自动分段; 运营发布前审核和编辑).</li>
 *   <li>GENERATED(3 系统生成): KnowledgeDocGenerator 定时任务自动生成; 数据源为商品/门店/分类静态结构化数据; 运营只读.</li>
 * </ul>
 */
public enum KbSourceType implements BaseEnum {

    /** 运营手动录入; KB 后台富文本编辑器创建; 完整编辑权限; 审计日志记录创建人 + 修改人. */
    MANUAL(1, "手动录入"),
    /** 文件上传批量导入; 支持 .md/.docx/.pdf/.txt 解析; 自动抽取文本并分段; 运营审核编辑后发布. */
    UPLOAD(2, "文件上传"),
    /** 系统定时任务自动生成; 数据源来自商品/门店/分类结构化数据; 运营只读不可直接编辑. */
    GENERATED(3, "系统生成");

    @EnumValue
    private final Integer code;

    private final String desc;

    KbSourceType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
