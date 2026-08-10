package com.retail.business.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员分页查询请求(会员中心列表页筛选/Agent 会员检索工具).
 * <p>分页参数由 {@link com.retail.core.interceptor.PageParameterInterceptor}
 * 从 HttpServletRequest 提取注入 ThreadLocal, 本 Req 不承载分页(分页为横切关注点, See 铁律 9).
 * <p>供 HTTP 接口与 member:query 工具共同复用.
 */
@Data
public class MemberQueryReq {

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
}
