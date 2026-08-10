package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.MovementType;
import com.retail.business.enums.StockBizType;

import java.time.LocalDateTime;

/**
 * 库存流水实体, 对应数据库 stock_movement 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); 门店级库存变动必须指定门店 id(NULL=租户中心仓调账).
 * <p>业务约束: 流水为不可变历史记录(物理删除表, 无 deleted 字段; 仅 created_at + createBy, 无更新); 每次库存变动必须先 SELECT product_stock FOR UPDATE 锁定账户, 再 INSERT 流水 + UPDATE 账户(同事务); 恒等式: beforeQty + changeQty = afterQty.
 * <p>幂等约束: UNIQUE(tenant_id, biz_type, biz_no), 同一业务单据只能产生 1 条库存流水(避免重复入/出库).
 */
@Data
@TableName("stock_movement")
public class StockMovement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入); NULL=租户中心仓(无门店上下文的盘点/调账场景); 门店级出库/入库必须填具体门店 id, 保证 product_stock 按门店对账准确. */
    private Long storeId;

    /** 商品 SPU id, 指向 product_info.id; 冗余便于按 SPU 维度聚合流水(商品出入库台账报表). */
    private Long productId;

    /** SKU id, 指向 product_sku.id; 无规格商品 = NULL, 此时按 product_id + store_id 锁定 product_stock 账户行. */
    private Long skuId;

    /** 库存账户 id, 指向 product_stock.id; 变动前必须先 SELECT product_stock FOR UPDATE 锁定此账户, 防止并发超卖(保证 beforeQty 读取的是最新值). */
    private Long stockId;

    /** 库存变动类型(MovementType 枚举本体: 1=INBOUND 入库, 2=OUTBOUND 出库, 3=ADJUST 调整, 4=LOCK 预占, 5=UNLOCK 释放, 6=PROFIT 盘盈, 7=LOSS 盘亏); 决定 UI 图标/颜色(入绿出红). */
    private MovementType movementType;

    /** 变动数量(整数); 正数 = 库存增加, 负数 = 库存减少; 恒等式校验: beforeQty + changeQty = afterQty(不成立则抛异常, 拒绝写流水). */
    private Integer changeQty;

    /** 变动前可用库存快照(从 product_stock.availableQty 读取的锁定后当前值); 便于审计追溯 + 流水反推账户余额. */
    private Integer beforeQty;

    /** 变动后可用库存快照(写入后同步 UPDATE product_stock.availableQty = afterQty); 与账户最终值一致, 流水快照永不改变. */
    private Integer afterQty;

    /** 关联业务类型(StockBizType 枚举本体: 1=ORDER 订单, 2=PURCHASE 采购, 3=ADJUST 手工调整, 4=REFUND 退款, 5=INVENTORY 盘点); 与 biz_no 组合决定幂等键 UNIQUE. */
    private StockBizType bizType;

    /** 关联业务单据号(如订单号 orderNo / 采购单号 purchaseNo / 盘点单号 inventoryNo); 与 biz_type 组合唯一, 防止重复写流水(幂等). */
    private String bizNo;

    /** 流水备注(给运营看的说明, 如 "618 采购批次 #PO20240601 入库" / "红色 T 恤 盘亏 2 件 待查明原因"); 库存台账列表展示用. */
    private String remark;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
