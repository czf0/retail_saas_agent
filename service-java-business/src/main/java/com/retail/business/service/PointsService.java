package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.PointsAdjustReq;
import com.retail.business.dto.resp.MemberPointsResp;
import com.retail.business.dto.resp.PointsLogResp;
import com.retail.business.entity.PointsLog;
import com.retail.business.enums.PointsBizType;

import java.time.LocalDate;

/**
 * 会员积分服务.
 * <p>
 * 所有积分变动方法(earn/exchange/refund/adjust)均返回 {@link PointsLog} 实体(含 before/after 余额快照),
 * 便于 OrderService 等跨模块调用方获取变动结果;Controller 层通过 PointsConvert 转换为 {@link PointsLogResp}.
 * <p>
 * 变动操作均标注 {@code @Transactional},保证流水写入与 member.points 更新原子性.
 */
public interface PointsService extends IService<PointsLog> {

    /**
     * 积分获取(订单完成时调用).
     *
     * @param memberId 会员ID
     * @param points   获取积分数量(正数)
     * @param bizType  业务类型,如 order/activity
     * @param bizNo    关联单据号,如订单号
     * @return 积分流水(含 before/after 余额快照)
     */
    PointsLog earn(Long memberId, Integer points, PointsBizType bizType, String bizNo);

    /**
     * 积分兑换扣减(校验余额充足).
     *
     * @param memberId 会员ID
     * @param points   兑换积分数量(正数,内部转换为负数扣减)
     * @param bizType  业务类型,如 coupon/manual
     * @param bizNo    关联单据号
     * @return 积分流水(含 before/after 余额快照)
     */
    PointsLog exchange(Long memberId, Integer points, PointsBizType bizType, String bizNo);

    /**
     * 退款扣减积分(订单退款时调用).
     *
     * @param memberId 会员ID
     * @param points   扣减积分数量(正数,内部转换为负数扣减)
     * @param bizNo    退款单号
     * @return 积分流水(含 before/after 余额快照)
     */
    PointsLog refund(Long memberId, Integer points, String bizNo);

    /**
     * 手动调整积分(正负均可).
     *
     * @param req 调整请求(memberId / changePoints / reason)
     * @return 积分流水(含 before/after 余额快照)
     */
    PointsLog adjust(PointsAdjustReq req);

    /**
     * 积分兑换扣减(写负数积分流水,校验余额充足).
     * <p>
     * 面向 Agent 工具 points:redeem,与 {@link #exchange} 的差异在于可携带兑换原因;内部复用
     * recordChange 以 EXCHANGE 变动类型 + MANUAL 业务类型写入流水,并更新 member.points 余额.
     *
     * @param memberId 会员ID
     * @param points   兑换积分数量(正数,内部转换为负数扣减)
     * @param reason   兑换原因(写入流水 remark,可空)
     * @return 积分流水(含 before/after 余额快照)
     */
    PointsLog redeem(Long memberId, Integer points, String reason);

    /**
     * 获取当前租户积分规则(1元=N积分).
     * <p>
     * 规则存储于 tenant_config.points_rate;未配置时返回默认值 1.
     *
     * @return 积分规则(1元=N积分)
     */
    // Integer getPointsRate();

    /**
     * 保存当前租户积分规则(1元=N积分).
     * <p>
     * 写入 tenant_config.points_rate,需租户配置已存在.
     *
     * @param rate 积分规则(1元=N积分,须为正整数)
     * @return 保存后的积分规则
     */
    Integer savePointsRate(Integer rate);

    /**
     * 分页查询会员积分流水.
     *
     * @param memberId   会员ID
     * @param changeType 变动类型过滤(可空)
     * @param startDate  开始日期(可空)
     * @param endDate    结束日期(可空)
     * @return 分页流水列表
     */
    PageResp<PointsLogResp> listLogs(Long memberId, Integer changeType, LocalDate startDate, LocalDate endDate);

    /**
     * 获取会员积分汇总(当前余额 + 累计获取/兑换 + 近30天变动).
     *
     * @param memberId 会员ID
     * @return 积分汇总
     */
    MemberPointsResp getPointsSummary(Long memberId);
}
