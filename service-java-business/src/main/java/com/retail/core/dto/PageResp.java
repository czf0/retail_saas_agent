package com.retail.core.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页响应封装(共享基础设施).
 * <p>business 与 rbac 模块共用,避免重复定义;微服务化时随 core 公共库迁移.
 */
@Data
public class PageResp<T> {
    private List<T> items;
    private Long total;
    private Integer page;
    private Integer pageSize;

    public PageResp(List<T> items, Long total, Integer page, Integer pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
