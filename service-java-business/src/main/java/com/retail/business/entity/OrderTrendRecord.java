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
 * 订单趋势统计快照实体, 对应数据库 order_trend 表.
 * <p>多租户 + 门店隔离(tenant_id / store_id 均由拦截器自动注入 WHERE 条件, 均无 @TableField(fill) 注解); store_id=NULL 表示租户级汇总(跨门店聚合).
 * <p>业务约束: 统计快照表(无 deleted 字段, 不软删除; 仅 created_at + createBy 审计字段, 无更新); 由定时任务 OrderTrendJob 每日凌晨按 (tenant, store, date) 维度汇总前一日订单数据写入, 避免报表实时 COUNT/SUM 大表.
 * <p>唯一约束: UNIQUE(tenant_id, store_id, stat_date), 同一统计维度同一日期只能有 1 条快照, 重复写入先 DELETE 再 INSERT(幂等).
 */
@Data
@TableName("order_trend")
public class OrderTrendRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 门店 id(StoreLineHandler 自动注入); NULL=租户级汇总(批量任务无门店上下文时跳过注入, 聚合所有门店数据写入); UNIQUE 组合索引包含此字段. */
    private Long storeId;

    /** 统计日期(Asia/Shanghai 时区, 粒度: 日); 例如 2024-06-01 00:00:00 表示 6 月 1 日全天统计; 报表按此字段 GROUP BY 画趋势折线图. */
    private LocalDateTime statDate;

    /** 订单总数(当天新建的订单数, 含所有状态); 报表 "订单量趋势" 指标 = orderCount, 环比 = (今天 - 昨天)/昨天. */
    private Integer orderCount;

    /** 订单总金额(当天新建订单的 pay_amount 之和, 单位: 元, 精度: 分, DECIMAL(12,2)); 报表 "GMV 趋势" 指标 = orderAmount, 环比同 orderCount. */
    private BigDecimal orderAmount;

    /** 退款单数(当天退款成功的订单数, 不含待审/拒绝); 报表 "退款趋势" 指标, 退款率 = refundCount / orderCount(前一日), 预警阈值默认 5%. */
    private Integer refundCount;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
}
