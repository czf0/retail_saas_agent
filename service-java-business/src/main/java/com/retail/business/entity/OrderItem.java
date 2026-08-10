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
 * 订单明细实体(商品快照), 对应数据库 order_item 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); 按订单维度创建, 与 order_info.order_id 强关联.
 * <p>业务约束: 物理删除表(无 deleted 字段), 仅 created_at + create_by 审计字段; 快照设计: product_name / category / sku_code / sku_spec / unit_price / cost_price 均为下单时快照值, 避免历史订单受商品后续修改影响.
 * <p>金额公式: subtotal = unit_price * qty(原价小计, 未分摊优惠前); order_info.discount_amount 按 subtotal 占比分摊到每条明细.
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
        private Long tenantId;
        private Long storeId;

    /** 订单 id, 指向 order_info.id; 创建明细时必须先 SELECT order_info FOR UPDATE 锁定订单行, 避免同一订单并发创建明细超卖. */
    private Long orderId;

    /** 冗余订单号(避免订单明细查询 JOIN order_info); 订单号生成后不可变, 冗余快照安全. */
    private String orderNo;

    /** 商品 SPU id, 指向 product_info.id; 用于按 SPU 维度聚合销售报表(商品详情页关联销量展示). */
    private Long productId;

    /** 商品名称快照(下单时从 product_info.name 读取写入); 后续商品改名不影响历史订单打印/财务对账(不可变凭证). */
    private String productName;

    /** 分类名称冗余快照(下单时从 product_info.category 读取写入); 用于报表按分类维度聚合销售数据, 避免 JOIN product_category. */
    private String category;

    /** SKU id, 指向 product_sku.id; 无规格商品(单规格)= NULL, 此时取 product_info.price/cost 作为快照. */
    private Long skuId;

    /** SKU 编码快照(下单时从 product_sku.sku_code 读取写入); 仓库拣货单 / 小票打印展示用. */
    private String skuCode;

    /** 规格描述快照(下单时 product_sku.specJson 拼接, 键值对用 "-" 连接, 多维度用 "/" 分隔, 如 "红色-XL/棉质"). */
    private String skuSpec;

    /** 成交单价(已含优惠分摊后价格, 单位: 元, 精度: 分, DECIMAL(12,2)); ≠ product_sku.price(原价), 含会员折扣/促销/券分摊后的最终单价. */
    private BigDecimal unitPrice;

    /** 购买数量(件, 必须 >= 1); 下单时预扣库存锁数量 = qty, 支付成功后真正扣减 product_stock.available_qty. */
    private Integer qty;

    /** 原价小计金额 = unit_price(原价)* qty; 注意: 此处 unit_price 已分摊优惠, 如需原价总额需反向计算; 最终订单 total_amount = SUM(order_item.subtotal). */
    private BigDecimal subtotal;

    /** 成本价快照(下单时从 product_sku.cost / product_info.cost 读取写入); 用于毛利报表 = subtotal - cost_price * qty, 商品改成本不影响历史毛利. */
    private BigDecimal costPrice;

    /** 已退款数量(部分退款时累加, OrderRefund APPROVED 时同步更新); 上限 = qty, 达到后该明细标记为"已全退"不再展示在售后可退列表. */
    private Integer refundQty;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
