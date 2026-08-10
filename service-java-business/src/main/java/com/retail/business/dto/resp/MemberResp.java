package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员详情页展示响应;聚合会员基础信息 + 等级/积分快照 + 消费统计(累计金额/订单数/RFM 关键时间戳).
 * <p>Controller: GET /api/v1/members/{id:\\d+};{id} 正则守卫;手机号/等级变更后有 5 分钟本地缓存(Caffeine).
 */
@Data
public class MemberResp {

    private Long id;

    private String name;

    private String phone;

    /** 会员等级:1=普通 2=银卡 3=金卡 4=钻石;见 {@link com.retail.business.enums.MemberLevelEnum}. */
    private Integer level;

    /** 当前可用积分(正整数,非冻结/过期部分);下单抵扣或积分商城消耗时扣减. */
    private Integer points;

    /** 累计消费金额(实付金额累计,单位: 元,精度: 分;订单 COMPLETED 时增量,全额退款时反向扣减). */
    private BigDecimal totalSpent;

    /** 累计订单数(订单完成时增量更新,用于客户报表) */
    private Integer totalOrders;

    /** 最后下单时间(用于 RFM 报表 Recency 计算) */
    private LocalDateTime lastOrderAt;

    /** 最后活跃时间(用于客户活跃度分析) */
    private LocalDateTime lastActiveAt;
}
