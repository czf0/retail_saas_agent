package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.KbDomain;

import java.time.LocalDateTime;

/**
 * 同义词表实体, 对应数据库 kb_synonym 表.
 * <p>隔离域: 已配置在 ignore-tables 中(scope=global 时 tenant_id=NULL 跨租户通用), 拦截器不自动注入 tenant_id 条件, Service 层手动按 scope + tenant_id 过滤(与 sys_user 同模式); 不进行门店隔离.
 * <p>业务约束: 同义词是确定性等价关系, 不用知识文档(模糊检索会引入近义误召); SSOT 是本表, Java 变更后同步写 Redis(key: kb:synonym:global / kb:synonym:{tenant}:{domain}), Python 只读 Redis, Redis 不可用降级跳过扩展.
 * <p>唯一约束: UNIQUE(scope, tenant_id, domain, term), 同范围同域下规范词不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("kb_synonym")
public class KbSynonym {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作用范围(枚举字符串: global 全局通用, tenant 租户特定); global 时 tenant_id=NULL 跨租户共享, tenant 时必填 tenant_id 仅本租户可见. */
    private String scope;

    /** 租户 id; scope=tenant 时填(仅本租户使用), scope=global 时 = NULL(跨租户通用同义词); UNIQUE 组合索引包含此字段. */
    private Long tenantId;

    /** 业务域(KbDomain 枚举本体: 1=ORDER 订单, 2=INVENTORY 库存 ... 9=STORE_LIST 门店列表); NULL=跨域通用(对所有 domain 生效); 同义词扩展时 domain 匹配优先, 跨域兜底. */
    private KbDomain domain;

    /** 规范词(canonical term, 同义词归一化的目标词); 如 "动销率" 同义词 -> 归一化为 "周转率", Agent 查询改写时用 term 替换原 query 词. */
    private String term;

    /** 同义词列表(JSON 数组字符串: ["动销","出货","销售流转"]); 数组内所有词均与 term 等价, 扩展时 query 命中数组词 -> 额外追加 term 到改写后 query(并集召回). */
    private String synonyms;
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
