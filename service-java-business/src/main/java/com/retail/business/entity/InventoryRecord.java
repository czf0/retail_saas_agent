package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存盘点统计快照实体, 对应数据库 inventory_record 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); store_id=NULL 表示租户级库存汇总.
 * <p>业务约束: 统计快照表(无 deleted 字段, 不软删除); 由定时任务 InventorySnapshotJob 每日凌晨按 (tenant, store, warehouse) 维度对 product_stock 当前库存做快照写入, 库存趋势报表扫此表不扫实时大表.
 * <p>唯一约束: UNIQUE(tenant_id, store_id, warehouse, product_name, created_at), 同维度同日只能 1 条(保证幂等).
 */
@Data
@TableName("inventory_record")
public class InventoryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入); NULL=租户级汇总(聚合所有门店库存快照); UNIQUE 组合索引包含此字段. */
    private Long storeId;

    /** 商品名称冗余快照(从 product_info.name 聚合); 库存报表按商品维度展示用, 避免 JOIN product_info 大表(盘点后改名不影响历史快照). */
    private String productName;

    /** 仓库名称(如 "中心仓"/"门店 1 号仓"/"退回仓"); 小型租户默认只有 "主仓", 多仓租户按此字段 GROUP BY 分别盘点. */
    private String warehouse;

    /** 盘点时账面库存数量(从 product_stock.available_qty 读取快照写入); 实际盘点物理数量 > stockQty = 盘盈, < stockQty = 盘亏, 写 stock_movement 调账. */
    private Integer stockQty;

    /** 安全库存阈值快照(从 product_stock.safetyStock 读取写入); 报表可统计 "当时 safetyStock 以下商品数" 趋势, 观察补货及时性改善. */
    private Integer safetyStock;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
