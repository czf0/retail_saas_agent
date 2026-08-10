package com.retail.business.dto.req;

import lombok.Data;

/**
 * 分类详情查询 Agent 工具入参.
 * <p>
 * 支持按分类ID或分类名称定位, 查询分类完整信息.
 */
@Data
public class CategoryDetailToolReq {

    /** 分类 ID(可选,优先使用;否则用 name 反查) */
    private Long categoryId;

    /** 分类名称(业务员无需知道分类ID) */
    private String name;
}
