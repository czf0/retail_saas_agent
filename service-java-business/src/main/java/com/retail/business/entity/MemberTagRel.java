package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 会员-标签关系实体, 对应数据库 member_tag_rel 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件, 无 @TableField(fill), 纯靠 SQL 层注入), 不进行门店隔离.
 * <p>业务约束: 物理删除表(无 deleted / 审计字段); 打标签时先按 member_id 物理 DELETE 旧关系, 再批量 INSERT 新关系(保证原子性); 与 sys_user_role / sys_role_menu 风格一致.
 * <p>唯一约束: UNIQUE(tenant_id, member_id, tag_id), 同一会员下不可重复绑定同一标签.
 */
@Data
@TableName("member_tag_rel")
public class MemberTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 会员 id, 指向 member.id; 绑定前 Service 层校验: 会员必须属于当前租户(通过 tenant_id 拦截器已保证, 双层保险). */
    private Long memberId;

    /** 标签 id, 指向 member_tag.id; 若标签被删除(逻辑删), 此关系保留历史快照, 列表查询时过滤掉 deleted=1 的标签. */
    private Long tagId;
}
