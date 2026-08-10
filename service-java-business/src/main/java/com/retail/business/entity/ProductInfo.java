package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SPU 主实体, 对应数据库 product_info 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(SPU 为租户全局统一商品主数据).
 * <p>业务约束: 一个 SPU 下可挂多个 SKU(product_sku.product_id 关联); 无 SKU 商品(单规格)默认创建 1 条 SKU 记录(sku_id 可空).
 * <p>唯一约束: UNIQUE(tenant_id, spu_code), 同一租户下 SPU 编码不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("product_info")
public class ProductInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 所属分类 id, 指向 product_category.id; 分类移动时此值同步更新, 历史订单快照不受影响. */
    private Long categoryId;

    /** 分类名称冗余快照, 避免商品列表查询 JOIN product_category; 分类改名时同步刷新增量. */
    private String category;

    /** 商品 SPU 编码(业务唯一键, UNIQUE(tenant_id, spu_code)); SKU 编码 = spu_code + 规格序号(如 SP001-001); 建议规则: 品类缩写 + 3 位序号. */
    private String spuCode;

    /** 品牌名(冗余便于检索与品牌报表聚合); 商品创建/更新时从品牌下拉选择写入, 不做外键关联. */
    private String brand;

    /** 零售价(SPU 默认展示价, POS 默认取值; 单位: 元, 精度: 分, BigDecimal, 数据库 DECIMAL(12,2); 最终成交价取 SKU.price, 此值仅列表展示). */
    private BigDecimal price;

    /** 成本价(SPU 默认成本; 单位: 元, 精度: 分, BigDecimal, 数据库 DECIMAL(12,2); 最终毛利分析取 SKU.cost, 此值仅初始化 SKU 用). */
    private BigDecimal cost;

    /** 上下架状态(ProductStatus 枚举本体: 1=ON_SHELF 上架, POS/商品列表可展示; 0=OFF_SHELF 下架, 不展示, 不影响已下单历史单据). */
    private ProductStatus status;

    private String description;

    private String imageUrl;

    /** 冗余 SPU 总库存(汇总各门店 product_stock.available_qty 之和); 库存变动时异步更新, 允许秒级延迟, 精确值以 product_stock 为准. */
    private Integer stockQty;

    /** 安全库存阈值(件); SPU 维度预警值, 无 SKU 商品直接用此值; 有 SKU 商品以 product_sku.safetyStock 优先(为 NULL 时回退此值). */
    private Integer safetyStock;

    /** 清仓标记(1=CLEARANCE 清仓, 0=NORMAL 正常); 清仓商品列表页打标, 促销引擎优先匹配, 报表单独统计清仓毛利. */
    private Integer clearance = 0;

    /** 保质期天数(临期预警用, NULL=非食品类无保质期); 临期预警规则: 入库批次日期 + shelfLifeDays - safetyDays <= now 时触发黄色预警; 入库批次日期 + shelfLifeDays <= now 时触发红色预警(禁止销售). */
    private Integer shelfLifeDays;

    /** 临期预警提前天数(天, 默认 7); 触发预警的时间窗口, 即到期前 safetyDays 天开始打临期标提醒运营; 与 shelfLifeDays 配合使用, 食品类建议 7-30 天, NULL 时回退全局默认 7. */
    // private Integer safetyDays;

    private Long tenantId;

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
