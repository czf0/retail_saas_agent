package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.retail.business.dto.resp.report.InventoryTurnoverResp;
import com.retail.business.dto.resp.report.SlowMovingResp;
import com.retail.business.entity.StockMovement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存流水 Mapper. 
 * <p>
 * stock_movement 为多租户 + 门店隔离表(物理删除), tenant_id / store_id 由拦截器自动注入查询条件. 
 * 基础 CRUD 由 {@link BaseMapper} 提供, 分页与条件查询在 Service 层使用 LambdaQueryWrapper 构建. 
 * <p>报表聚合查询方法供 InventoryReportService 消费. 
 */
@Mapper
public interface StockMovementMapper extends BaseMapper<StockMovement> {

    /**
     * 显式指定门店写入库存流水(绕过拦截器, store_id 由调用方指定). 
     * <p>
     * 调拨 {@code stock:transfer} 需要为源/目标两个不同门店分别落流水, 门店拦截器按当前用户
     * 门店注入无法覆盖目标门店, 故此处用 @InterceptorIgnore 关闭租户 + 门店两个
     * TenantLineInnerInterceptor, 改为显式传入 tenant_id / store_id / create_by. 
     * movementType / bizType 为带 @EnumValue 的枚举, MyBatis 自动映射为 Integer code. 
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO stock_movement (tenant_id, store_id, product_id, sku_id, stock_id, movement_type, " +
            "change_qty, before_qty, after_qty, biz_type, biz_no, remark, created_at, create_by) " +
            "VALUES (#{tenantId}, #{storeId}, #{productId}, #{skuId}, #{stockId}, #{movementType}, " +
            "#{changeQty}, #{beforeQty}, #{afterQty}, #{bizType}, #{bizNo}, #{remark}, NOW(), #{createBy})")
    int insertIgnoreTenant(StockMovement movement);

    /**
     * 库存周转率 - 出库成本: 按 product_id 聚合时间范围内出库流水的总数量 × 成本价. 
     * <p>JOIN product_info 获取成本价; movement_type='outbound' 为销售出库. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 各商品的出库成本列表
     */
    @Select("<script>" +
            "SELECT sm.product_id AS product_id, " +
            "COALESCE(pi.name, CONCAT('商品#', sm.product_id)) AS product_name, " +
            "COALESCE(SUM(ABS(sm.change_qty)) * MAX(pi.cost), 0) AS outbound_cost " +
            "FROM stock_movement sm LEFT JOIN product_info pi ON sm.product_id = pi.id AND pi.deleted = 0 " +
            "WHERE sm.movement_type = 2 " +  // 2 = MovementType.OUTBOUND(出库)
            "<if test='startDate != null'>AND sm.created_at &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND sm.created_at &lt;= #{endDate} </if>" +
            "GROUP BY sm.product_id, pi.name" +
            "</script>")
    List<InventoryTurnoverResp> selectOutboundCost(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * 滞销商品: 查询在指定时间范围内无出库动销记录的商品. 
     * <p>从 product_stock 中取出有库存的商品, LEFT JOIN stock_movement 的 outbound 记录, 
     * 筛选最近出库时间为空或超出时间范围的商品. 
     *
     * @param startDate 起始时间(用于判断滞销区间起点)
     * @param endDate   结束时间(用于计算未销售天数)
     * @return 滞销商品列表
     */
    @Select("<script>" +
            "SELECT ps.product_id AS product_id, " +
            "COALESCE(pi.name, CONCAT('商品#', ps.product_id)) AS product_name, " +
            "last_out.last_out_time AS last_out_time, " +
            "SUM(ps.available_qty) AS stock_qty " +
            "FROM product_stock ps " +
            "LEFT JOIN product_info pi ON ps.product_id = pi.id AND pi.deleted = 0 " +
            "LEFT JOIN (SELECT product_id, MAX(created_at) AS last_out_time " +
            "           FROM stock_movement WHERE movement_type = 2 " +
            "           <if test='startDate != null'>AND created_at &gt;= #{startDate} </if>" +
            "           <if test='endDate != null'>AND created_at &lt;= #{endDate} </if>" +
            "           GROUP BY product_id) last_out ON ps.product_id = last_out.product_id " +
            "WHERE ps.deleted = 0 AND ps.available_qty > 0 AND last_out.last_out_time IS NULL " +
            "GROUP BY ps.product_id, pi.name, last_out.last_out_time " +
            "ORDER BY stock_qty DESC" +
            "</script>")
    List<SlowMovingResp> selectSlowMoving(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}
