package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.dto.resp.report.CategorySalesResp;
import com.retail.business.dto.resp.report.ProductSalesRankResp;
import com.retail.business.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单明细 Mapper. 
 * <p>基础 CRUD 由 BaseMapper 提供; {@link #addRefundQty} 为部分退款时增量累加退款数量的自定义方法. 
 * <p>报表聚合查询方法供 SalesReportService 消费, tenant_id / store_id 由拦截器自动注入. 
 */
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 增量累加明细的退款数量(部分退款时调用). 
     *
     * @param itemId     明细ID
     * @param refundQty  本次退款数量
     * @return 影响行数(0=超出 qty 上限, 业务层据此判定非法退款)
     */
    @Update("UPDATE order_item SET refund_qty = refund_qty + #{refundQty} " +
            "WHERE id = #{itemId} AND qty - refund_qty >= #{refundQty}")
    int addRefundQty(@Param("itemId") Long itemId,
                     @Param("refundQty") Integer refundQty);

    // ===================== 报表聚合查询(供 SalesReportService 消费) =====================

    /**
     * 商品销售排行: JOIN order_info 过滤有效订单, 按 product_id 分组聚合销量与金额. 
     * <p>仅统计 paid/shipped/completed 状态订单, 按 sales_amount 降序排列. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @param productId 商品ID(可空, 指定时仅查该商品)
     * @return 商品销售排行列表
     */
    @Select("<script>" +
            "SELECT oi.product_id AS product_id, " +
            "oi.product_name AS product_name, " +
            "oi.category AS category, " +
            "SUM(oi.qty) AS qty, " +
            "COALESCE(SUM(oi.subtotal), 0) AS sales_amount " +
            "FROM order_item oi INNER JOIN order_info o ON oi.order_id = o.id " +
            "WHERE o.deleted = 0 AND o.status IN (2,3,4) " +  // 2=PAID 3=SHIPPED 4=COMPLETED(OrderStatus, 已付款及以上=有效销售)
            "<if test='startDate != null'>AND o.order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND o.order_time &lt;= #{endDate} </if>" +
            "<if test='productId != null'>AND oi.product_id = #{productId} </if>" +
            "GROUP BY oi.product_id, oi.product_name, oi.category " +
            "ORDER BY sales_amount DESC" +
            "</script>")
    List<ProductSalesRankResp> selectProductSalesRank(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate,
                                                       @Param("productId") Long productId);

    /**
     * 分类销售占比: JOIN order_info 过滤有效订单, 按 category 分组聚合金额与订单数. 
     *
     * @param startDate 起始时间(可空)
     * @param endDate   结束时间(可空)
     * @return 分类销售列表
     */
    @Select("<script>" +
            "SELECT oi.category AS category_name, " +
            "COALESCE(SUM(oi.subtotal), 0) AS sales_amount, " +
            "COUNT(DISTINCT oi.order_id) AS order_count " +
            "FROM order_item oi INNER JOIN order_info o ON oi.order_id = o.id " +
            "WHERE o.deleted = 0 AND o.status IN (2,3,4) " +
            "AND oi.category IS NOT NULL AND oi.category != '' " +
            "<if test='startDate != null'>AND o.order_time &gt;= #{startDate} </if>" +
            "<if test='endDate != null'>AND o.order_time &lt;= #{endDate} </if>" +
            "GROUP BY oi.category ORDER BY sales_amount DESC" +
            "</script>")
    List<CategorySalesResp> selectCategorySales(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);
}
