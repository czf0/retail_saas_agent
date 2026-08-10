package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.dto.resp.report.FinanceSummaryResp;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.dto.resp.report.OrderFunnelResp;
import com.retail.business.dto.resp.report.PayTypeDistResp;
import com.retail.business.dto.resp.report.SalesSummaryResp;
import com.retail.business.dto.resp.report.SalesTrendResp;
import com.retail.business.dto.resp.report.StoreSalesCompareResp;
import com.retail.business.entity.OrderInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper. 
 * <p>基础 CRUD 由 BaseMapper 提供; {@link #addRefundAmount} / {@link #markStatus} 为自定义增量更新方法, 
 * 保证并发退款场景下 refund_amount 累加的原子性与状态机一致性. 
 * <p>报表聚合查询方法(selectSalesSummary 等)供 ReportService 直接消费, 
 * tenant_id / store_id 由拦截器自动注入过滤条件, SQL 中无需显式声明. 
 */
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 增量累加退款金额(退款审核通过时调用, 避免并发覆盖). 
     *
     * @param orderId      订单ID
     * @param refundAmount 本次退款金额
     * @return 影响行数
     */
    @Update("UPDATE order_info SET refund_amount = refund_amount + #{refundAmount}, " +
            "updated_at = CURRENT_TIMESTAMP, update_by = 'system' " +
            "WHERE id = #{orderId} AND pay_amount - refund_amount >= #{refundAmount}")
    int addRefundAmount(@Param("orderId") Long orderId,
                        @Param("refundAmount") BigDecimal refundAmount);

    /**
     * 标记订单状态(状态机变更, 如 paid→shipped→completed). 
     *
     * @param orderId    订单ID
     * @param newStatus  目标状态
     * @param finishTime 完成时间(仅 completed 时非空)
     * @return 影响行数
     */
    @Update("UPDATE order_info SET status = #{newStatus}, " +
            "finish_time = COALESCE(#{finishTime}, finish_time), " +
            "updated_at = CURRENT_TIMESTAMP, update_by = 'system' " +
            "WHERE id = #{orderId}")
    int markStatus(@Param("orderId") Long orderId,
                   @Param("newStatus") Integer newStatus,
                   @Param("finishTime") LocalDateTime finishTime);

    // ===================== 报表聚合查询(供 ReportService 消费) =====================

    /**
     * 销售汇总: 聚合 order_info 的 GMV / 订单数 / 客单价 / 退款金额 / 优惠金额. 
     * <p>仅统计非 pending 状态订单(已支付及之后状态), tenant_id / store_id 由拦截器自动过滤. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 销售汇总数据; 无数据时返回全零对象
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(pay_amount), 0) AS total_gmv, " +
            "COUNT(*) AS order_count, " +
            "COALESCE(AVG(pay_amount), 0) AS avg_order_value, " +
            "COALESCE(SUM(refund_amount), 0) AS refund_amount, " +
            "COALESCE(SUM(discount_amount), 0) AS total_discount " +
            "FROM order_info WHERE deleted = 0 AND status != 1 " +  // 1 = OrderStatus.PENDING(待付款)
            "<if test='startDate != null'>AND order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND order_time &lt;= #{endDate} </if>" +
            "</script>")
    SalesSummaryResp selectSalesSummary(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * 门店销售对比: 按 store_id 分组聚合销售金额 / 订单数 / 客单价. 
     * <p>LEFT JOIN sys_store 获取门店名称; store_id 为 NULL 时标记为"租户中心仓". 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 各门店销售对比列表
     */
    @Select("<script>" +
            "SELECT o.store_id AS store_id, " +
            "COALESCE(s.store_name, '租户中心仓') AS store_name, " +
            "COALESCE(SUM(o.pay_amount), 0) AS sales_amount, " +
            "COUNT(*) AS order_count, " +
            "COALESCE(AVG(o.pay_amount), 0) AS avg_order_value " +
            "FROM order_info o LEFT JOIN sys_store s ON o.store_id = s.id AND s.deleted = 0 " +
            "WHERE o.deleted = 0 AND o.status != 1 " +
            "<if test='startDate != null'>AND o.order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND o.order_time &lt;= #{endDate} </if>" +
            "GROUP BY o.store_id, s.store_name ORDER BY sales_amount DESC" +
            "</script>")
    List<StoreSalesCompareResp> selectStoreCompare(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * 销售趋势: 按 DATE(order_time) 分组聚合每日销售金额与订单数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 每日销售趋势列表
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(o.order_time, '%Y-%m-%d') AS date, " +
            "COALESCE(SUM(o.pay_amount), 0) AS sales_amount, " +
            "COUNT(*) AS order_count " +
            "FROM order_info o WHERE o.deleted = 0 AND o.status != 1 " +
            "<if test='startDate != null'>AND o.order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND o.order_time &lt;= #{endDate} </if>" +
            "GROUP BY DATE_FORMAT(o.order_time, '%Y-%m-%d') ORDER BY date" +
            "</script>")
    List<SalesTrendResp> selectSalesTrend(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * 订单转化漏斗: 按 status 分组统计各阶段订单数. 
     * <p>阶段包括 pending / paid / shipped / completed / closed / refunding / refunded. 
     *
     * @return 各状态订单数列表
     */
    @Select("SELECT status AS stage, COUNT(*) AS count FROM order_info " +
            "WHERE deleted = 0 GROUP BY status ORDER BY FIELD(status, 1,2,3,4,5,6,7)")  // 1=PENDING 2=PAID 3=SHIPPED 4=COMPLETED 5=CLOSED 6=REFUNDING 7=REFUNDED(OrderStatus)
    List<OrderFunnelResp> selectOrderFunnel();

    /**
     * 支付方式分布: 按 pay_type 分组聚合金额与订单数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 各支付方式分布列表
     */
    @Select("<script>" +
            "SELECT pay_type AS pay_type, " +
            "COALESCE(SUM(pay_amount), 0) AS amount, " +
            "COUNT(*) AS order_count " +
            "FROM order_info WHERE deleted = 0 AND status != 1 AND pay_type IS NOT NULL " +
            "<if test='startDate != null'>AND order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND order_time &lt;= #{endDate} </if>" +
            "GROUP BY pay_type ORDER BY amount DESC" +
            "</script>")
    List<PayTypeDistResp> selectPayTypeDist(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * 财务汇总: 聚合总收入 / 退款金额 / 优惠金额 / 订单数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 财务汇总数据
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(pay_amount), 0) AS total_revenue, " +
            "COALESCE(SUM(refund_amount), 0) AS refund_amount, " +
            "COALESCE(SUM(pay_amount) - SUM(refund_amount), 0) AS net_revenue, " +
            "COALESCE(SUM(discount_amount), 0) AS discount_amount, " +
            "COUNT(*) AS order_count " +
            "FROM order_info WHERE deleted = 0 AND status != 1 " +
            "<if test='startDate != null'>AND order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND order_time &lt;= #{endDate} </if>" +
            "</script>")
    FinanceSummaryResp selectFinanceSummary(@Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * 会员增长 - 活跃会员数: 按 DATE(order_time) 分组统计每日下单的不同会员数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 每日活跃会员数列表
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(order_time, '%Y-%m-%d') AS date, " +
            "COUNT(DISTINCT member_id) AS active_members " +
            "FROM order_info WHERE deleted = 0 AND status != 1 AND member_id IS NOT NULL " +
            "<if test='startDate != null'>AND order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND order_time &lt;= #{endDate} </if>" +
            "GROUP BY DATE_FORMAT(order_time, '%Y-%m-%d') ORDER BY date" +
            "</script>")
    List<MemberGrowthResp> selectActiveMemberByDate(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    /**
     * 客单价分析 - 总商品件数: 统计时间范围内所有订单明细的总购买数量. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 总商品件数
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(oi.qty), 0) FROM order_item oi " +
            "INNER JOIN order_info o ON oi.order_id = o.id " +
            "WHERE o.deleted = 0 AND o.status != 1 " +
            "<if test='startDate != null'>AND o.order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND o.order_time &lt;= #{endDate} </if>" +
            "</script>")
    Integer selectTotalItemsCount(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);
}
