package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.dto.resp.report.MemberLevelDistResp;
import com.retail.business.entity.Member;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员 Mapper. 
 * <p>基础 CRUD 由 BaseMapper 提供; {@link #incTotalOrders} 为自定义增量更新方法, 
 * 由订单完成流程同事务调用, 保证 member 表汇总字段与订单数据强一致. 
 * <p>报表聚合查询方法供 MemberReportService 消费. member 表为租户隔离表(非门店隔离), 
 * tenant_id 由拦截器自动注入, store_id 需在 Service 层手动附加(通常会员为租户级数据不按门店过滤). 
 */
public interface MemberMapper extends BaseMapper<Member> {

    /**
     * 增量更新会员汇总字段(订单完成时调用). 
     * <p>累计订单数 +1, 累计消费金额累加, 最后下单时间更新. 
     * 使用 SET 表达式避免并发覆盖问题. 
     *
     * @param memberId  会员ID
     * @param amount    本次订单实付金额
     * @param orderTime 下单时间(用于更新 last_order_at)
     * @return 影响行数(0=会员不存在或租户隔离未命中)
     */
    @Update("UPDATE member SET total_orders = total_orders + 1, " +
            "total_spent = total_spent + #{amount}, " +
            "last_order_at = #{orderTime}, " +
            "last_active_at = #{orderTime}, " +
            "updated_at = CURRENT_TIMESTAMP, " +
            "update_by = 'system' " +
            "WHERE id = #{memberId}")
    int incTotalOrders(@Param("memberId") Long memberId,
                       @Param("amount") BigDecimal amount,
                       @Param("orderTime") LocalDateTime orderTime);

    // ===================== 报表聚合查询(供 MemberReportService 消费) =====================

    /**
     * 会员等级分布: 按 level 分组统计人数. 
     *
     * @return 各等级会员数列表
     */
    @Select("SELECT level AS level, COUNT(*) AS member_count " +
            "FROM member GROUP BY level ORDER BY FIELD(level, 4,3,2,1)")  // 4=DIAMOND 3=GOLD 2=SILVER 1=NORMAL(MemberLevel, 降序排列)
    List<MemberLevelDistResp> selectLevelDist();

    /**
     * 会员增长 - 新增会员数: 按 DATE(created_at) 分组统计每日新注册会员数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 每日新增会员数列表
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, " +
            "COUNT(*) AS new_members " +
            "FROM member " +
            "<where>" +
            "<if test='startDate != null'>AND created_at &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND created_at &lt;= #{endDate} </if>" +
            "</where>" +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY date" +
            "</script>")
    List<MemberGrowthResp> selectMemberGrowth(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
}
