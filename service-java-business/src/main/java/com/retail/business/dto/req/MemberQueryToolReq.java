package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Agent 工具专用入参: 会员查询工具(member:query, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>会员定位: 支持 name/phone/level 等多维自然语言解析, 不只依赖 memberId(铁律 20).
 */
@Data
public class MemberQueryToolReq {

    private String name;

    private String phone;

    /** MemberLevel 枚举 code: 1=NORMAL 普通 2=SILVER 银卡 3=GOLD 金卡 4=DIAMOND 钻石. */
    private Integer level;

    /** 积分下限(含); 与 maxPoints 组合区间查询. */
    private Integer minPoints;

    /** 积分上限(含); 与 minPoints 组合区间查询. */
    private Integer maxPoints;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 累计消费金额下限(含). */
    private BigDecimal minTotalSpent;

    /** 单位: 元, 精度: 分 (BigDecimal, 2 dp); 累计消费金额上限(含). */
    private BigDecimal maxTotalSpent;

    /** 累计订单数下限(含). */
    private Integer minTotalOrders;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;

    /** 排序方向标记; true=降序, false/null=升序. */
    private Boolean isDesc;
}
