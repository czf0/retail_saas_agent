package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.retail.business.enums.MemoryCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 长期记忆实体(跨会话用户主观偏好 SSOT), 对应数据库 long_memory 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(用户跨门店偏好全局一致).
 * <p>业务约束: 只存用户主观偏好/约束/习惯/目标, 不存业务实体(门店/SKU/库存/GMV 等以 Java 实时数据为准); 同分类槽位: category 0-6 每 (tenant,user) 固定 1 条, OTHER(100) 允许 MEMORY_OTHER_SLOT_MAX 条; confidence 为入库门槛, 新记忆必须 >= 阈值才能合并/覆盖.
 * <p>唯一约束: UNIQUE(tenant_id, user_id, category), category=OTHER(100) 时退化为普通索引不唯一.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("long_memory")
public class LongMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 用户 id(关联 sys_user.id, 按用户维度隔离长期记忆; 列表查询 Service 层手动追加 user_id = 当前用户过滤). */
    private Long userId;

    /** 记忆类型(枚举字符串: preference 偏好 / constraint 约束 / habit 习惯 / goal 目标); v1 默认 preference, 后续扩展类型支持不同的合并策略(如 goal 永不覆盖需人工确认). */
    private String memoryType;

    /** 分类槽位(MemoryCategory 枚举本体: 1=PREFERRED_BRAND 偏好品牌, 2=SIZE 尺码, 3=PRICE_RANGE 价格区间, 4=PAY_METHOD 支付方式, 5=COMMUNICATION_STYLE 沟通风格, 6=SCENE 场景, 100=OTHER 其他); 1-6 每用户固定 1 条槽位, 100 允许多条. */
    private MemoryCategory category;

    /** 记忆文本(用户主观偏好/约束自然语言描述, 如 "喜欢购买纯棉材质的 T 恤, 不接受聚酯纤维"); Agent 调用前通过 Mem0ContextNode 注入到 LLM system prompt. */
    private String content;

    /** 置信度(BigDecimal 0.00-1.00, LLM 抽取标注分数); 合并/覆盖规则: 新记忆 confidence >= MEMORY_CONFIDENCE_THRESHOLD(默认 0.70)才能入库, < 阈值丢弃不持久化. */
    private BigDecimal confidence;

    /** 重要性等级(1-5 整数分); 1=随口一提(易覆盖), 5=硬性不可违背(覆盖必须人工确认, Agent 工具修改前需用户确认). */
    private Integer importance;

    /** 访问次数(正整数, 命中一次 + 1); 用于记忆重排加权: accessCount 越高排序越靠前, 配合 lastAccessedAt recency 组合打分. */
    private Integer accessCount;

    /** 最近访问时间(Asia/Shanghai 时区); 用于记忆重排 recency 加权: 最近访问的记忆排序越靠前; accessCount/lastAccessedAt 均在命中时同步更新. */
    private LocalDateTime lastAccessedAt;

    /** 最近一次来源消息 id(关联 chat_message.id); 用于增量游标回溯: 长记忆系统从 source_msg_id 起扫描 chat_message 增量抽取新记忆, 避免重复处理历史消息. */
    private Long sourceMsgId;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        private Integer deleted = 0;
        private LocalDateTime deleteAt;
        private String deleteBy;
}
