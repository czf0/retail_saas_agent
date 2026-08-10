package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员标签定义实体, 对应数据库 member_tag 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(标签为租户全局统一配置).
 * <p>业务约束: 标签按租户维度定义, 会员通过 member_tag_rel 多对多关联; 系统标签(is_system=1 保留扩展)不可删除, 自定义标签可编辑删除.
 * <p>唯一约束: UNIQUE(tenant_id, tag_name), 同一租户下标签名不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("member_tag")
public class MemberTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 标签名称(UNIQUE(tenant_id, tag_name), 租户内唯一); 建议规则: 业务域前缀 + 语义(如 RFM_H_VALUE 高价值, 沉睡 90_DAY_SLEEP). */
    private String tagName;

    /** 标签展示色(前端标签 chip 组件背景色, HEX 格式如 #FF6B6B); 相同类型标签建议同色系, NULL 时前端取默认灰色 #9CA3AF. */
    private String tagColor;

    /** 标签业务语义描述(给运营看, 说明打标签规则/适用场景, 如 "过去 90 天无下单, 且累计消费 >= 500 元的高价值沉睡用户"). */
    private String description;
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
