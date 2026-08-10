package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.report.CouponRedeemResp;
import com.retail.business.dto.resp.report.CouponRoiResp;
import com.retail.business.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户优惠券 Mapper. 
 * <p>报表聚合查询方法供 CouponReportService 消费, tenant_id / store_id 由拦截器自动注入. 
 * <p>
 * <b>连表查询说明</b>: {@link #selectUserCouponPage} 通过 LEFT JOIN member 一次性带出会员名称, 
 * 消除前端数据孤岛. user_coupon 为逻辑删除表(手动 t.deleted = 0), member 无 deleted 字段; 
 * MyBatis-Plus 3.5.6 的 TenantLineInnerInterceptor 将 member 表 tenant_id 注入到 ON 子句, 不破坏外连接. 
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 按会员 + 状态查询用户优惠券(无分页, 用于会员侧列表展示). 
     * <p>tenant_id / store_id 由拦截器自动追加过滤条件, 无需在 SQL 中显式声明. 
     *
     * @param memberId 会员 ID
     * @param status   券状态, nullable 表示不限
     * @return 用户优惠券列表
     */
    @Select("SELECT * FROM user_coupon WHERE member_id = #{memberId} " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "AND deleted = 0 ORDER BY receive_time DESC")
    List<UserCoupon> selectByMemberAndStatus(@Param("memberId") Long memberId, @Param("status") Integer status);

    /**
     * 分页查询用户优惠券列表(LEFT JOIN member 带出会员名称). 
     * <p>
     * 使用 LEFT JOIN: 理论上 user_coupon.member_id 始终非空(领券必填会员), 但 LEFT JOIN 更安全. 
     * user_coupon 为逻辑删除表, 手动声明 t.deleted = 0; member 无 deleted 字段. 
     * <p>
     * tenant_id / store_id 由拦截器自动注入: 
     * <ul>
     *   <li>user_coupon(主表): tenant_id → WHERE, store_id → WHERE(白名单注入)</li>
     *   <li>member(LEFT JOIN 表): tenant_id → ON 子句, 不破坏外连接; store_id 不注入(member 不在白名单)</li>
     * </ul>
     *
     * @param page      分页对象(由 PageContextHolder 提供)
     * @param memberId  会员ID(可空)
     * @param status    券状态(可空)
     * @param couponId  模板ID(可空)
     * @param startDate 领取起始时间(可空)
     * @param endDate   领取结束时间(可空)
     * @return 分页结果, 记录直接映射到 UserCouponListItemResp(含 memberName)
     */
    @Select("<script>" +
            "SELECT t.id, t.coupon_id, t.coupon_name, t.coupon_type, t.member_id, " +
            "m.name AS member_name, " +
            "t.status, t.order_id, t.order_no, t.face_value, t.threshold, " +
            "t.receive_time, t.used_time, t.expire_time " +
            "FROM user_coupon t " +
            "LEFT JOIN member m ON t.member_id = m.id " +
            "WHERE t.deleted = 0 " +
            "<if test='memberId != null'>AND t.member_id = #{memberId} </if>" +
            "<if test='status != null'>AND t.status = #{status} </if>" +
            "<if test='couponId != null'>AND t.coupon_id = #{couponId} </if>" +
            "<if test='startDate != null'>AND t.receive_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND t.receive_time &lt;= #{endDate} </if>" +
            "ORDER BY t.receive_time DESC" +
            "</script>")
    IPage<UserCouponListItemResp> selectUserCouponPage(IPage<UserCouponListItemResp> page,
                                                       @Param("memberId") Long memberId,
                                                       @Param("status") Integer status,
                                                       @Param("couponId") Long couponId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    // ===================== 报表聚合查询(供 CouponReportService 消费) =====================

    /**
     * 优惠券核销率: 按 coupon_id 分组统计发放数, 已使用数. 
     *
     * @param startDate 起始时间(可空, 按领取时间过滤)
     * @param endDate   结束时间(可空)
     * @return 各优惠券核销率列表
     */
    @Select("<script>" +
            "SELECT coupon_id AS coupon_id, " +
            "MAX(coupon_name) AS coupon_name, " +
            "COUNT(*) AS issued_count, " +
            "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS used_count " +  // 2 = CouponStatus.USED(已使用)
            "FROM user_coupon WHERE deleted = 0 " +
            "<if test='startDate != null'>AND receive_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND receive_time &lt;= #{endDate} </if>" +
            "GROUP BY coupon_id ORDER BY issued_count DESC" +
            "</script>")
    List<CouponRedeemResp> selectRedeemRate(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * 优惠券营销 ROI: 按 coupon_id 聚合折扣金额与带来销售额. 
     * <p>JOIN order_info 获取使用该券的订单实付金额(broughtSales); 
     * 折扣金额取 user_coupon.face_value 之和(仅已核销的券). 
     *
     * @param startDate 起始时间(可空, 按使用时间过滤)
     * @param endDate   结束时间(可空)
     * @return 各优惠券 ROI 列表
     */
    @Select("<script>" +
            "SELECT uc.coupon_id AS coupon_id, " +
            "MAX(uc.coupon_name) AS coupon_name, " +
            "COALESCE(SUM(uc.face_value), 0) AS discount_amount, " +
            "COALESCE(SUM(o.pay_amount), 0) AS brought_sales " +
            "FROM user_coupon uc LEFT JOIN order_info o ON uc.order_id = o.id AND o.deleted = 0 " +
            "WHERE uc.deleted = 0 AND uc.status = 2 " +
            "<if test='startDate != null'>AND uc.used_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND uc.used_time &lt;= #{endDate} </if>" +
            "GROUP BY uc.coupon_id ORDER BY brought_sales DESC" +
            "</script>")
    List<CouponRoiResp> selectCouponRoi(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
}
