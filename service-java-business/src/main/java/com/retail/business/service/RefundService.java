package com.retail.business.service;

import com.retail.business.dto.req.RefundAuditReq;
import com.retail.business.dto.req.RefundCreateReq;
import com.retail.business.dto.req.RefundQueryReq;
import com.retail.business.dto.resp.RefundAuditResp;
import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.dto.resp.RefundResp;
import com.retail.core.dto.PageResp;

/**
 * 退款服务接口.
 * <p>封装退款申请 → 审核 → 退款的完整流程.
 * <p>跨模块联动(审核通过时同事务执行):
 * <ul>
 *   <li>退券:{@code UserCouponService.refundByOrder}</li>
 *   <li>退积分:{@code PointsService.refund}</li>
 *   <li>库存回滚:{@code StockService.inbound}</li>
 *   <li>订单 refund_amount 累加 + 全额退则订单状态改 refunded</li>
 * </ul>
 */
public interface RefundService {

    /**
     * 创建退款申请.
     * <p>校验:订单状态为 PAID/SHIPPED/COMPLETED,退款金额不超过可退金额(pay_amount - 已退金额).
     * 状态初始化为 PENDING.订单状态同步标记为 REFUNDING.
     */
    RefundResp createRefund(RefundCreateReq req);

    /**
     * 审核退款单.
     * <p>审核通过时事务内执行退款联动(退券/退积分/库存回滚/订单 refund_amount 累加);
     * 全额退款完成则订单状态改 REFUNDED,部分退款保持原状态.
     * 审核拒绝时订单状态回退到原状态.
     */
    RefundAuditResp auditRefund(Long refundId, RefundAuditReq req);

    /**
     * 撤销待审核退款单.
     * <p>仅 {@code PENDING} 状态的退款单可撤销;撤销后退款单状态置为 {@code CANCELLED},
     * 关联订单从退款中(REFUNDING)恢复退款前原状态.
     *
     * @param refundId 退款单ID
     * @return 撤销后的退款单信息(含状态)
     */
    RefundResp cancel(Long refundId);

    /**
     * 查询退款单详情.
     */
    RefundResp getRefund(Long refundId);

    /**
     * 分页查询退款单列表.
     */
    PageResp<RefundListItemResp> listRefunds(Integer status,
                                              String orderNo,
                                              String startDate,
                                              String endDate);

    /**
     * 分页查询退款单列表(业务语义过滤).
     * <p>
     * 在原有基础上额外支持会员姓名/手机号(先反查会员ID集合再 IN),退款类型,退款金额区间过滤.
     */
    PageResp<RefundListItemResp> listRefunds(RefundQueryReq req);
}
