package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.retail.business.dto.resp.report.StockAlertResp;
import com.retail.business.dto.resp.report.StockFundResp;
import com.retail.business.entity.ProductStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商品库存账户 Mapper. 
 * <p>
 * product_stock 为多租户 + 门店隔离表, tenant_id / store_id 由拦截器自动注入查询条件, 
 * 故自定义查询无需显式拼接 store_id / tenant_id(拦截器自动附加). 
 */
@Mapper
public interface ProductStockMapper extends BaseMapper<ProductStock> {

    /**
     * 按商品 + SKU 查询库存账户(store_id / tenant_id 由拦截器自动附加过滤). 
     * <p>
     * skuId 为空时匹配 sku_id IS NULL 的账户(无规格商品); 唯一键 uk_tenant_store_sku 保证最多一条. 
     *
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @return 库存账户; 不存在返回 null
     */
    @Select("<script>" +
            "SELECT * FROM product_stock WHERE deleted = 0 AND product_id = #{productId} " +
            "<if test='skuId != null'>AND sku_id = #{skuId} </if>" +
            "<if test='skuId == null'>AND sku_id IS NULL </if>" +
            "LIMIT 1" +
            "</script>")
    ProductStock selectByProductAndSku(@Param("productId") Long productId, @Param("skuId") Long skuId);

    // ===================== 跨门店操作(调拨用, 绕过租户/门店拦截器, 显式指定 store_id) =====================

    /**
     * 按 租户×门店×商品×SKU 查询库存账户(显式指定 store_id, 绕过拦截器). 
     * <p>
     * 供门店间调拨 {@code stock:transfer} 使用: 当前用户可能与源/目标门店不同, 
     * 门店拦截器按当前用户门店注入条件无法覆盖目标门店, 故此处用 @InterceptorIgnore 关闭
     * 租户 + 门店两个 TenantLineInnerInterceptor, 改为 SQL 显式传入 tenant_id / store_id. 
     *
     * @param tenantId  租户ID
     * @param storeId   门店ID
     * @param productId 商品ID
     * @param skuId     SKU ID(无规格商品传 null)
     * @return 库存账户; 不存在返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>" +
            "SELECT * FROM product_stock WHERE deleted = 0 AND tenant_id = #{tenantId} AND store_id = #{storeId} " +
            "AND product_id = #{productId} " +
            "<if test='skuId != null'>AND sku_id = #{skuId} </if>" +
            "<if test='skuId == null'>AND sku_id IS NULL </if>" +
            "LIMIT 1" +
            "</script>")
    ProductStock selectByStoreSku(@Param("tenantId") Long tenantId, @Param("storeId") Long storeId,
                                  @Param("productId") Long productId, @Param("skuId") Long skuId);

    /**
     * 显式指定门店创建库存账户(绕过拦截器, store_id 由调用方指定). 
     * <p>供调拨目标门店无账户时创建; tenant_id / store_id / create_by 显式入参. 
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO product_stock (tenant_id, store_id, product_id, sku_id, available_qty, locked_qty, " +
            "in_transit_qty, safety_stock, deleted, created_at, create_by) " +
            "VALUES (#{tenantId}, #{storeId}, #{productId}, #{skuId}, #{availableQty}, 0, 0, 0, 0, NOW(), #{createBy})")
    int insertIgnoreTenant(ProductStock stock);

    /**
     * 显式指定门店更新可用库存(绕过拦截器, 按 id + tenant_id 定位). 
     * <p>仅用于存量非并发路径(如 setSafetyStock 后刷新账户的场景); 库存扣加统一改用
     * {@link #decreaseAvailable} / {@link #increaseAvailable} 原子 SQL 防丢失更新. 
     *
     * @param id          库存账户ID
     * @param tenantId    租户ID(防跨租户误更新)
     * @param availableQty 新的可用库存
     * @return 受影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE product_stock SET available_qty = #{availableQty}, updated_at = NOW() " +
            "WHERE id = #{id} AND tenant_id = #{tenantId}")
    int updateAvailableIgnoreTenant(@Param("id") Long id, @Param("tenantId") Long tenantId,
                                    @Param("availableQty") Integer availableQty);

    // ===================== 库存扣加原子 SQL(并发安全, 消除"先读后写"丢失更新) =====================

    /**
     * 原子扣减可用库存.
     * <p>{@code UPDATE SET available_qty = available_qty - ? WHERE id=? AND tenant_id=? AND deleted=0 AND available_qty >= ?},
     * MySQL InnoDB 行锁保证原子性; 受影响行数 = 0 表示库存不足或账户不存在/已删除, 调用方据此抛异常. 
     * 绕过租户拦截器显式传 tenant_id, 同时兼容当前门店(adjust/inbound/outbound/count)与跨门店(transfer)两条路径. 
     *
     * @param id       库存账户ID
     * @param tenantId 租户ID(防跨租户误更新)
     * @param qty      扣减数量(正数)
     * @return 受影响行数; 0=库存不足或账户不存在
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE product_stock " +
            "SET available_qty = available_qty - #{qty}, updated_at = NOW() " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0 " +
            "AND available_qty >= #{qty}")
    int decreaseAvailable(@Param("id") Long id,
                          @Param("tenantId") Long tenantId,
                          @Param("qty") int qty);

    /**
     * 原子增加可用库存.
     * <p>{@code UPDATE SET available_qty = available_qty + ? WHERE id=? AND tenant_id=? AND deleted=0}, 
     * 与 {@link #decreaseAvailable} 配套用于入库/盘盈/调拨目标增加. 
     *
     * @param id       库存账户ID
     * @param tenantId 租户ID
     * @param qty      增加数量(正数)
     * @return 受影响行数; 0=账户不存在或已删除
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE product_stock " +
            "SET available_qty = available_qty + #{qty}, updated_at = NOW() " +
            "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
    int increaseAvailable(@Param("id") Long id,
                          @Param("tenantId") Long tenantId,
                          @Param("qty") int qty);

    /**
     * 原子扣加完成后读取新的可用库存(绕过拦截器显式 id 定位). 
     * <p>与 {@link #decreaseAvailable}/{@link #increaseAvailable} 配对使用, 
     * 事务内同一行上的 SELECT 能拿到刚 UPDATE 的值(MVCC 读自己事务的未提交写), 
     * 据此反推 before_qty = after_qty ∓ changeQty, 保证流水 before/after 快照绝对准确. 
     *
     * @param id 库存账户ID
     * @return 当前可用库存; 不存在/已删除返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT available_qty FROM product_stock WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Integer selectAvailableById(@Param("id") Long id);

    // ===================== 报表聚合查询(供 InventoryReportService 消费) =====================

    /**
     * 缺货预警: 查询可用库存低于安全库存阈值的商品. 
     * <p>JOIN product_info 获取商品名称; 按 product_id 汇总各 SKU 的可用库存. 
     * <p>
     * B-18 修复: 原 ORDER BY 引用 {@code ps.safety_stock - ps.available_qty}, 
     * 这两列既不在 GROUP BY 中也未聚合, 触发 MySQL {@code only_full_group_by} 模式报错. 
     * 改用聚合函数 {@code MAX(ps.safety_stock) - SUM(ps.available_qty)} 直接计算, 
     * 避免使用别名(别名 safety_stock 与 WHERE 子句中的列名 ps.safety_stock 冲突, MySQL 报 ambiguous). 
     *
     * @return 缺货预警列表
     */
    @Select("SELECT ps.product_id AS product_id, " +
            "COALESCE(pi.name, CONCAT('商品#', ps.product_id)) AS product_name, " +
            "SUM(ps.available_qty) AS stock_qty, " +
            "MAX(ps.safety_stock) AS safety_stock " +
            "FROM product_stock ps LEFT JOIN product_info pi ON ps.product_id = pi.id AND pi.deleted = 0 AND pi.tenant_id = ps.tenant_id " +
            "WHERE ps.deleted = 0 AND ps.available_qty < ps.safety_stock " +
            "GROUP BY ps.product_id, pi.name " +
            "ORDER BY (MAX(ps.safety_stock) - SUM(ps.available_qty)) DESC")
    List<StockAlertResp> selectStockAlerts();

    /**
     * 库存资金占用: 按商品维度汇总库存数量 × 成本价, 计算库存价值. 
     * <p>JOIN product_info 获取商品名称与成本价; 按 product_id 聚合各 SKU 库存. 
     *
     * @return 库存资金占用列表
     */
    @Select("SELECT ps.product_id AS product_id, " +
            "COALESCE(pi.name, CONCAT('商品#', ps.product_id)) AS product_name, " +
            "SUM(ps.available_qty) AS stock_qty, " +
            "COALESCE(MAX(pi.cost), 0) AS unit_cost, " +
            "COALESCE(SUM(ps.available_qty) * MAX(pi.cost), 0) AS stock_value " +
            "FROM product_stock ps LEFT JOIN product_info pi ON ps.product_id = pi.id AND pi.deleted = 0 AND pi.tenant_id = ps.tenant_id " +
            "WHERE ps.deleted = 0 " +
            "GROUP BY ps.product_id, pi.name " +
            "ORDER BY stock_value DESC")
    List<StockFundResp> selectStockFund();
}
