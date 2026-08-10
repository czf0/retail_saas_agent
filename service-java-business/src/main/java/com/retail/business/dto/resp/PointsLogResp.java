package com.retail.business.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员积分流水详情/列表行项;展示单笔积分变动(类型/前后余额/来源单据号);积分流水为不可变追加写历史,不可删除.
 * <p>Controller: GET /api/v1/members/{memberId:\\d+}/points-logs;按时间倒序,20 条/页.
 */
@Data
public class PointsLogResp {

    private Long id;

    private Long memberId;

    /** 会员名称(Service 层查询 member 后填充,消除前端数据孤岛) */
    private String memberName;

    /** 变动类型:1=EARN(下单赠送) 2=GIFT(活动赠送) 3=EXCHANGE(积分商城兑换扣减) 4=REFUND(订单退款反向扣回) 5=ADJUST(后台人工调整);见 PointsChangeTypeEnum. */
    private Integer changeType;

    /** 变动积分值(正数=增加,负数=扣减;|changePoints| = afterBalance - beforeBalance 恒等式校验用). */
    private Integer changePoints;

    /** 变动前可用积分余额(扣减时用于校验 beforeBalance >= |changePoints|,防超扣). */
    private Integer beforeBalance;

    /** 变动后可用积分余额(写库原子性更新:UPDATE balance = balance + changePoints). */
    private Integer afterBalance;

    /** 关联业务类型:1=ORDER(订单) 2=COUPON(优惠券) 3=MANUAL(人工) 4=ACTIVITY(营销活动);决定了 bizNo 的含义. */
    private Integer bizType;

    /** 关联业务单据号(bizType=ORDER 时=orderNo;bizType=COUPON 时=couponCode;人工为空). */
    private String bizNo;

    /** 备注(人工调整必填原因;如"积分过期清零"/"客服补偿"). */
    private String remark;

    private LocalDateTime createdAt;

    /** 操作人(人工调整=后台用户 username;系统触发='system';下单='member:#{memberId}'). */
    private String createBy;
}
