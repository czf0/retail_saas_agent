package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 长期记忆分类枚举(严格对齐 Python 端 MemoryCategory IntEnum 编码值).
 * <p>核心分类 0-6 每个 (tenant, user, category) 元组固定 1 个槽位 - 达到置信度阈值时新记忆 UPDATE 覆写; 100=OTHER 允许多个槽位, 溢出时淘汰最低重要度项:
 * <ul>
 *   <li>REPORT_FORMAT(0 报表格式): 周报/日报布局, 同比/环比展示偏好, 导出文件格式(xlsx/pdf/csv).</li>
 *   <li>SCOPE_FILTER(1 数据范围): 门店过滤默认值, 时间范围默认值(本月/本季), 分类子集偏好.</li>
 *   <li>PERMISSION_CONFIRM(2 确认要求): 改价 / 调库存需预先确认, 批量操作禁用开关.</li>
 *   <li>DIAGNOSIS_DEPTH(3 诊断深度): 需深挖根因, 结论优先回答, 简洁/冗长回答长度.</li>
 *   <li>DISPLAY_STYLE(4 展示风格): Markdown 表格 vs 图表 vs 纯文本偏好, 暗色模式偏好.</li>
 *   <li>PROMO_PREFERENCE(5 促销偏好): 满减优先于折扣, 短视频 + 导购话术偏好.</li>
 *   <li>COMMUNICATION_STYLE(6 沟通风格): 正式语气 vs 口语化, Emoji 禁用偏好.</li>
 *   <li>OTHER(100 其他): 兜底分类; 允许多槽, 超 MEMORY_OTHER_SLOT_MAX 时淘汰最低重要度项.</li>
 * </ul>
 */
public enum MemoryCategory implements BaseEnum {

    /** 报表格式偏好槽; 存储周报/日报类型, 同比/环比展示, 导出文件格式偏好; 用户稳定偏好, 变更时覆写. */
    REPORT_FORMAT(0, "报表格式"),
    /** 数据/时间范围过滤偏好槽; 存储默认门店范围, 默认时间范围(本月/本季), 默认分类子集过滤. */
    SCOPE_FILTER(1, "数据范围"),
    /** 权限/确认要求偏好槽; 存储改价/调库存需显式确认, 批量操作禁用标记. */
    PERMISSION_CONFIRM(2, "确认要求"),
    /** 诊断深度/回答长度偏好槽; 存储深挖根因开关, 结论优先排序, 回答简洁度等级. */
    DIAGNOSIS_DEPTH(3, "诊断深度"),
    /** 展示风格偏好槽; 存储 Markdown 表格 / 图表 / 纯文本偏好, 暗色模式主题偏好. */
    DISPLAY_STYLE(4, "展示风格"),
    /** 促销偏好槽; 存储满减优先于折扣, 短视频 + 导购话术偏好. */
    PROMO_PREFERENCE(5, "促销偏好"),
    /** 沟通风格偏好槽; 存储正式/口语语气, Emoji 允许/禁用, 问候语风格. */
    COMMUNICATION_STYLE(6, "沟通风格"),
    /** 其他稳定偏好兜底分类; 允许多槽, 溢出时淘汰最低重要度项; from_code 查询未知编码时的默认回退. */
    OTHER(100, "其他");

    @EnumValue
    private final Integer code;
    private final String desc;

    MemoryCategory(Integer code, String desc) {
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

    /** 是否核心分类(0-6, 每类固定 1 个槽位) */
    public boolean isCore() {
        return code >= 0 && code <= 6;
    }

    /** 根据 Integer 编码查找枚举; 非法值回退 OTHER(对齐 Python from_code) */
    public static MemoryCategory fromCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        for (MemoryCategory c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        return OTHER;
    }
}
