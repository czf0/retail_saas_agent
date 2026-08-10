package com.retail.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI Agent 长期记忆抽取/巩固 → 单条操作结果响应(Java←Python 内部 DTO,落库 Java 侧消费);一条 operation = 对一张 memory_record 的 add/update/delete 动作.
 * <p>幂等:同一 targetId 多次 update 按 operation 顺序合并;Java 侧落库时若 category Integer 非法,回退为默认 OTHER(100).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryOperationResp {

    /** 操作类型:add=新增行 / update=按 targetId 更新内容 / delete=按 targetId 软删除;字符串枚举. */
    private String op;

    /** 记忆大类枚举 code(Integer;0-6=业务分类 100=OTHER;落库时 Java 转 MemoryCategory 枚举本体,非法值兜底 OTHER). */
    private Integer category;

    /** 记忆正文内容(add 必填;update 非空才覆盖;delete 忽略);Python 侧截断前 200 字符. */
    private String content;

    /** 置信度(Python 侧过滤后 0.5-1.00;<0.5 operation 不产出,减少 Java 无效落库). */
    private BigDecimal confidence;

    /** 重要性评分(Python importance scorer 打分;整数 1-5). */
    private Integer importance;

    /** 目标记忆 ID(update/delete 必填,定位 memory_record.id;add = NULL 由 Java 雪花算法生成). */
    private Long targetId;
}
