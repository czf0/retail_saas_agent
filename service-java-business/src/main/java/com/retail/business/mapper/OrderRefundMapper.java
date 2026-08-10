package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.retail.business.dto.resp.report.RefundAnalysisResp;
import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.entity.OrderRefund;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款单 Mapper. 
 * <p>基础 CRUD 由 BaseMapper 提供. 退款金额累加, 状态机变更由 Service 层通过 updateById 完成. 
 * <p>报表聚合查询方法供 OrderReportService 消费, tenant_id / store_id 由拦截器自动注入. 
 * <p>
 * <b>连表查询说明</b>: {@link #selectRefundPage} 通过 LEFT JOIN member 一次性带出会员名称, 
 * 消除前端数据孤岛. MyBatis-Plus 3.5.6 的 TenantLineInnerInterceptor 会将 member 表的 tenant_id
 * 条件注入到 LEFT JOIN 的 ON 子句(而非 WHERE), 故 member_id 为 NULL 的散客退款行不会被过滤. 
 */
public interface OrderRefundMapper extends BaseMapper<OrderRefund> {

    /**
     * 退款分析: 聚合退款总金额 / 退款笔数 / 全额退款数 / 部分退款数 / 平均退款金额. 
     * <p>仅统计已退款(status='refunded')的退款单. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 退款分析数据
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(refund_amount), 0) AS total_refund_amount, " +
            "COUNT(*) AS refund_order_count, " +
            "SUM(CASE WHEN refund_type = 1 THEN 1 ELSE 0 END) AS full_refund_count, " +  // 1 = RefundType.FULL(全额退款)
            "SUM(CASE WHEN refund_type = 2 THEN 1 ELSE 0 END) AS partial_refund_count, " +  // 2 = RefundType.PARTIAL(部分退款)
            "COALESCE(AVG(refund_amount), 0) AS avg_refund_amount " +
            "FROM order_refund WHERE status = 4 " +  // 4 = RefundStatus.REFUNDED(已退款)
            "<if test='startDate != null'>AND apply_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND apply_time &lt;= #{endDate} </if>" +
            "</script>")
    RefundAnalysisResp selectRefundAnalysis(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * 分页查询退款列表(LEFT JOIN member 带出会员名称). 
     * <p>
     * 使用 LEFT JOIN 而非 INNER JOIN: 散客订单退款(member_id 为 NULL)也需展示. 
     * member.name 别名为 member_name, 由 MyBatis-Plus 下划线转驼峰映射到 memberName 字段. 
     * <p>
     * tenant_id 过滤由 TenantLineInnerInterceptor 自动注入: 
     * <ul>
     *   <li>order_refund(主表): 注入到 WHERE 子句</li>
     *   <li>member(LEFT JOIN 表): 注入到 ON 子句, 不破坏外连接语义</li>
     * </ul>
     * store_id 由 StoreLineHandler 白名单注入(order_refund 在白名单中). 
     *
     * @param page      分页对象(由 PageContextHolder 提供, 含 page/pageSize)
     * @param status    退款状态(可空)
     * @param orderNo   订单号模糊查询(可空)
     * @param startDate 申请起始时间(可空)
     * @param endDate   申请结束时间(可空)
     * @return 分页结果, 记录直接映射到 RefundListItemResp(含 memberName)
     */
    @Select("<script>" +
            "SELECT t.id, t.refund_no, t.order_id, t.order_no, t.member_id, " +
            "m.name AS member_name, " +
            "t.refund_type, t.refund_amount, t.refund_qty, t.reason, " +
            "t.status, t.apply_time, t.refund_time, t.created_at " +
            "FROM order_refund t " +
            "LEFT JOIN member m ON t.member_id = m.id " +
            "WHERE 1 = 1 " +
            "<if test='status != null'>AND t.status = #{status} </if>" +
            "<if test='orderNo != null and orderNo != \"\"'>AND t.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='startDate != null'>AND t.apply_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND t.apply_time &lt;= #{endDate} </if>" +
            "ORDER BY t.id DESC" +
            "</script>")
    IPage<RefundListItemResp> selectRefundPage(IPage<RefundListItemResp> page,
                                               @Param("status") Integer status,
                                               @Param("orderNo") String orderNo,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);

    /**
     * 分页查询退款列表(业务语义过滤, LEFT JOIN member 带出会员名称). 
     * <p>
     * 在 {@link #selectRefundPage} 基础上额外支持退款类型, 退款金额区间, 会员ID集合过滤. 
     * memberIds 由 Service 层按会员姓名/手机号反查得到(先查ID再过滤). 
     *
     * @param page       分页对象
     * @param status     退款状态(可空)
     * @param orderNo    订单号模糊查询(可空)
     * @param startDate  申请起始时间(可空)
     * @param endDate    申请结束时间(可空)
     * @param refundType 退款类型(可空, 1全额/2部分)
     * @param minAmount  最低退款金额(可空)
     * @param maxAmount  最高退款金额(可空)
     * @param memberIds  会员ID集合(可空, 非空时 IN 过滤)
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT t.id, t.refund_no, t.order_id, t.order_no, t.member_id, " +
            "m.name AS member_name, " +
            "t.refund_type, t.refund_amount, t.refund_qty, t.reason, " +
            "t.status, t.apply_time, t.refund_time, t.created_at " +
            "FROM order_refund t " +
            "LEFT JOIN member m ON t.member_id = m.id " +
            "WHERE 1 = 1 " +
            "<if test='status != null'>AND t.status = #{status} </if>" +
            "<if test='orderNo != null and orderNo != \"\"'>AND t.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='startDate != null'>AND t.apply_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND t.apply_time &lt;= #{endDate} </if>" +
            "<if test='refundType != null'>AND t.refund_type = #{refundType} </if>" +
            "<if test='minAmount != null'>AND t.refund_amount &gt;= #{minAmount} </if>" +
            "<if test='maxAmount != null'>AND t.refund_amount &lt;= #{maxAmount} </if>" +
            "<if test='memberIds != null and memberIds.size() > 0'>" +
            "AND t.member_id IN " +
            "<foreach collection='memberIds' item='mid' open='(' separator=',' close=')'>#{mid}</foreach> " +
            "</if>" +
            "ORDER BY t.id DESC" +
            "</script>")
    IPage<RefundListItemResp> selectRefundPageByReq(IPage<RefundListItemResp> page,
                                                    @Param("status") Integer status,
                                                    @Param("orderNo") String orderNo,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate,
                                                    @Param("refundType") Integer refundType,
                                                    @Param("minAmount") BigDecimal minAmount,
                                                    @Param("maxAmount") BigDecimal maxAmount,
                                                    @Param("memberIds") List<Long> memberIds);
}
