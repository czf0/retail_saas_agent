package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品销售统计快照实体, 对应数据库 sales_record 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); store_id=NULL 表示租户级汇总.
 * <p>业务约束: 统计快照表(无 deleted 字段, 不软删除; 仅 created_at + createBy); 由定时任务 SalesTrendJob 每日凌晨按 (tenant, store, date, product) 维度汇总前一日销售数据写入, 商品维度报表不扫大表 order_item.
 * <p>唯一约束: UNIQUE(tenant_id, store_id, record_date, product_name, category), 同维度同日只能 1 条(保证幂等).
 */
@Data
@TableName("sales_record")
public class SalesRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入); NULL=租户级汇总(聚合所有门店商品销售数据); UNIQUE 组合索引包含此字段. */
    private Long storeId;

    /** 商品名称冗余快照(从 order_item.product_name 聚合); 报表商品 TOP 榜按此维度 GROUP BY 展示, 避免 JOIN product_info 大表. */
    private String productName;

    /** 分类名称冗余快照(从 order_item.category 聚合); 报表分类销售占比按此维度 GROUP BY 画饼图, 避免 JOIN product_category. */
    private String category;

    /** 销售总金额(当天该商品的 order_item.subtotal 之和, 已分摊优惠后金额; 单位: 元, 精度: 分, DECIMAL(12,2)); 商品销售榜排序 = salesAmount DESC. */
    private BigDecimal salesAmount;

    /** 销售总件数(当天该商品 SUM(order_item.qty - refundQty), 减去已退数量; 件数榜单独排序, 如纸巾这种低价高件数商品). */
    private Integer salesQty;

    /** 订单次数(当天包含该商品的订单总数, 按 order_id DISTINCT 计数); 用于连带率分析 = orderCount / 总订单数. */
    private Integer orderCount;

    /** 统计记录日期(Asia/Shanghai 时区, 粒度: 日); 例如 2024-06-01 00:00:00 表示 6 月 1 日全天销售数据聚合. */
    private LocalDateTime recordDate;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
