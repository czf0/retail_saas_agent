package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品库存账户实体, 对应数据库 product_stock 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件); store_id = NULL 表示租户中心仓(无门店上下文时默认落点).
 * <p>业务约束: 按 (SKU × 门店) 维度的库存账户主表; 所有库存变动(下单/采购/调拨/盘盈亏)必须先锁定账户行(SELECT ... FOR UPDATE)再写流水, 保证 available_qty + locked_qty + in_transit_qty = 账户恒等式.
 * <p>唯一约束: UNIQUE(sku_id, store_id), 同一门店下同一 SKU 只能有 1 条库存账户记录(无 SKU 商品 sku_id = NULL, 退化为 UNIQUE(product_id, store_id)).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("product_stock")
public class ProductStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 id(TenantInterceptor 自动注入 WHERE 条件, 无 @TableField(fill)). */
    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入); NULL=租户中心仓(跨门店调拨中转仓 / 批量任务无上下文落点); UNIQUE(sku_id, store_id) 组合索引包含此字段. */
    private Long storeId;

    /** 商品 SPU id, 指向 product_info.id; 冗余字段便于按 SPU 聚合查询门店库存汇总. */
    private Long productId;

    /** SKU id, 指向 product_sku.id; 无规格商品 = NULL, 此时 UNIQUE(product_id, store_id) 生效(sku_id 不参与 NULL 值唯一判断). */
    private Long skuId;

    /** 可用库存(件, 可售数量 = 当前用户前台看到的库存); 下单预扣: available -= qty, locked += qty; 支付成功: locked -= qty(真正扣减); 支付超时/关单: locked -= qty, available += qty(释放). */
    private Integer availableQty;

    /** 锁定库存(件, 待付款订单临时占用); 所有下单必须先 SELECT ... FOR UPDATE 锁定账户行, 避免并发超卖: available >= qty 校验后再原子扣减. */
    private Integer lockedQty;

    /** 在途库存(件, 采购订单已审核但未入库的数量); 采购入库时: in_transit -= qty, available += qty; 用于可用库存 + 在途库存 合并展示给运营补货决策. */
    private Integer inTransitQty;

    /** 安全库存阈值(件); 当 available_qty <= safetyStock 时, 库存列表页 belowSafety = true 提醒运营补货; 由 ProductSkuServiceImpl.batchOnShelf 上架时初始化默认 10, 可手动改. */
    private Integer safetyStock;
        private Integer deleted = 0;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
