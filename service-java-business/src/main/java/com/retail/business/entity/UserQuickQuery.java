package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户快捷提问实体, 对应数据库 user_quick_query 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件, 本表 tenant_id 非空, 不在 ignore-tables), 不进行门店隔离.
 * <p>业务约束: 评审 D8 canonical_query 一鱼三吃(检索缓存 key + 范式路由 key + 快捷命中); 用户保存常用问法 shortcutText -> 绑定 canonicalQuery, 输入命中即直接跳转; 管理员可设 isPublic=1 的租户级公共快捷提问(全租户共享, userId=NULL).
 * <p>唯一约束: UNIQUE(tenant_id, user_id, shortcut_text), 同一用户下同快捷问法不可重复; public=1 时 userId=NULL 退化为 UNIQUE(tenant, shortcut_text).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("user_quick_query")
public class UserQuickQuery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 用户 id(关联 sys_user.id); 个人快捷提问填具体用户 id, 列表 Service 层手动追加 user_id = 当前用户过滤; isPublic=1 时此值 = NULL(租户级公共快捷提问不绑定具体用户). */
    private Long userId;

    /** 是否为租户级公共快捷提问(1=PUBLIC 租户级共享, 0=PRIVATE 个人私有); PUBLIC 类型仅管理员可增删改, 普通用户只读; 公共快捷提问展示在会话页 "推荐问法" 卡片. */
    private Integer isPublic;

    /** 快捷提问文本(用户输入的原始问法, 如 "看下昨天销量多少"); Agent 输入框联想匹配时通过前缀 LIKE 匹配命中, 命中后直接用 canonicalQuery 替换 query. */
    private String shortcutText;

    /** 规范化查询语句(与范式路由 canonical_query 完全对齐, 如 "查询昨日全门店销量与环比"); 一鱼三吃用途: ① 向量检索缓存 key ② 范式路由缓存 key ③ 快捷命中直接走此 query, 大幅提升命中率. */
    private String canonicalQuery;

    /** 业务场景编码(与 paradigm_router scenario 枚举对齐, 如 order_query / metric_definition / promotion_analysis); NULL=未分类, 先路由再兜底; 非空时直接跳过范式路由命中对应场景节点. */
    private String scenario;
        @TableField(fill = FieldFill.INSERT)
    private String createBy;
        @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
        private Integer deleted = 0;
}
