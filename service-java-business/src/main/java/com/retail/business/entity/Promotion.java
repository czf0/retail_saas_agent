package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.PromotionType;
import com.retail.business.enums.TargetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 促销活动实体, 对应数据库 promotion 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(活动为租户全局配置, 可按门店范围限制 targetIds).
 * <p>业务约束: 活动引擎规则容器, 同一商品同一时间只能命中 1 个活动(按优先级 priority 高者优先, 避免多重折扣叠加亏损); targetIds / rules 为 JSON 字段, 依赖 JacksonTypeHandler + autoResultMap=true; 活动结束后定时任务自动置 status=EXPIRED.
 * <p>唯一约束: UNIQUE(tenant_id, name), 同一租户下活动名不可重复; 时间重叠冲突 Service 层校验(不通过 DB 唯一索引).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName(value = "promotion", autoResultMap = true)
public class Promotion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 促销活动名称(UNIQUE(tenant_id, name), 租户内不可重复); 建议命名: 时间 + 类型 + 范围, 如 "618 全场 8 折大促", 运营报表检索用. */
    private String name;

    /** 活动类型(PromotionType 枚举本体: 1=COUPON 发券活动, 2=DISCOUNT 直接折扣, 3=SECKILL 限时秒杀); 类型决定 rules 字段结构: DISCOUNT 需 discount_rate, SECKILL 需 flash_price + stock_limit. */
    private PromotionType type;

    /** 适用范围目标类型(TargetType 枚举本体: 1=ALL 全场商品, 2=PRODUCT 指定商品, 3=CATEGORY 指定分类); 决定 targetIds 列表的语义: ALL 时 targetIds 为空, PRODUCT 存商品 ID, CATEGORY 存分类 ID(不是名称). */
    private TargetType targetType;

    /** 适用对象 ID 列表(JSON 数组字符串); 语义由 targetType 决定: PRODUCT 时 = [product_id_1, product_id_2], CATEGORY 时 = [category_id_1, category_id_2], ALL 时 = [](空数组). */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetIds;

    /** 活动状态(PromotionStatus 枚举本体: 1=PENDING 未开始, 2=ENABLED 进行中, 3=EXPIRED 已结束); 定时任务 PromotionJob 每分钟扫描: startTime 到了 -> ENABLED, endTime 过了 -> EXPIRED(无需人工操作). */
    private PromotionStatus status;

    /** 活动开始时间(Asia/Shanghai 时区, 含此时间点); < now 且 status=PENDING 时, 定时任务自动启动活动; 支持预约未来活动("618 提前配置, 当天 0 点自动生效"). */
    private LocalDateTime startTime;

    /** 活动结束时间(Asia/Shanghai 时区, 含此时间点); > now 且 status=ENABLED 时, 定时任务自动结束活动; 秒杀活动通常只持续几小时, 结束后商品恢复原价. */
    private LocalDateTime endTime;

    /** 活动规则容器(Map<String,Object> JSON, 结构由 type 决定); DISCOUNT 类型: {discount_rate: 0.80, stackable: false}; SECKILL 类型: {flash_price: 9.90, stock_limit: 100, per_user_limit: 1}; COUPON 类型: {coupon_template_ids: [1,2,3]}. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rules;
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
