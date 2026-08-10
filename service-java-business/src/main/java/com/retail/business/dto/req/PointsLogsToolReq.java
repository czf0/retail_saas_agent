package com.retail.business.dto.req;

import lombok.Data;

/**
 * Agent 工具专用入参: 会员积分流水查询工具(points:logs, Agent 自然语言解析后调用).
 * <p>分页: 本 ToolReq 允许承载 page/pageSize 字段; Agent 反射调用工具不经过 HTTP 拦截器,
 * 业务代码需手动 PageContextHolder.set(PageContextHolder.build(page,pageSize)) + finally { PageContextHolder.clear() }(铁律 9).
 * <p>会员定位: 支持 memberId/memberName/phone 多维自然语言解析, 不只依赖 memberId(铁律 20).
 */
@Data
public class PointsLogsToolReq {

    /** 目标会员 id, 对应 member.id; 可选, 优先使用; 否则用 memberName/phone 反查. */
    private Long memberId;

    private String memberName;

    private String phone;

    /** PointsChangeType 枚举 code: 1=EARN 消费获取 2=GIFT 活动赠送 3=EXCHANGE 兑换消耗 4=REFUND 退款扣减 5=ADJUST 手动调整. */
    private Integer changeType;

    /** 流水起始日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String startDate;

    /** 流水截止日期(yyyy-MM-dd, Asia/Shanghai); 可空. */
    private String endDate;

    /** 页码; 默认 1; 正整数; ToolReq 手动注入 PageContextHolder. */
    private Integer page = 1;

    /** 每页条数; 默认 20; 正整数, 上限 100(Service 层校验); ToolReq 手动注入 PageContextHolder. */
    private Integer pageSize = 20;
}
