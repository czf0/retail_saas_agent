package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 会员沉睡识别工具(member:sleeping, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>沉睡定义: last_active_at 距今超过 days 天的会员(无消费/活跃).
 */
@Data
public class MemberSleepingToolReq {

    /** 无活跃天数阈值; 必填, >=1; 如 90 表示超 90 天未活跃; Service 层校验范围. */
    private Integer days;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
