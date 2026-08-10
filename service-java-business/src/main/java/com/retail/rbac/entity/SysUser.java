package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.rbac.enums.SysStatus;

import java.time.LocalDateTime;

/**
 * 系统用户实体, 对应数据库 sys_user 表(替换原 sys_account).
 * <p>隔离域: 已配置在多租户拦截器 ignore-tables 中(tenant_id 可空, NULL=平台管理员), 拦截器不自动注入 tenant_id 条件, 相关查询在 Service 层手动按 tenant_id 过滤.
 * <p>业务约束: tenant_id / store_id 由 {@link com.retail.core.config.AuditMetaObjectHandler} 在插入时从登录上下文自动填充(非空时填充, 平台管理员保持 null).
 * <p>通用审计字段说明: deleted 由全局逻辑删除配置管理(0=未删除, 1=已删除); createBy / updateBy 由全局 MetaObjectHandler 自动填充; deleteAt / deleteBy 由 BaseServiceImpl 在逻辑删除时显式填充(See: {@link com.retail.core.service.BaseServiceImpl#removeById}).
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 id(拦截器不自动注入, 插入时 MetaObjectHandler 填充); NULL=平台管理员(跨租户可见所有数据), 非空=租户级用户(仅见本租户数据). */
    private Long tenantId;

    /** 门店 id(插入时 MetaObjectHandler 填充); NULL=无固定门店(租户管理员/平台管理员), 非空=门店级用户(StoreLineHandler 自动注入此值到 SQL WHERE). */
    private Long storeId;

    private String username;

    /** bcrypt 算法密码哈希(不可反解); 登录校验 BCrypt.checkpw(明文, passwordHash), 密码重置由管理员生成重置链接. */
    private String passwordHash;

    private String nickName;

    private String email;

    private String phone;

    /** 性别(整型枚举值: 0=UNKNOWN 未知, 1=MALE 男, 2=FEMALE 女); 会员画像/精准营销分组使用, 可由用户修改. */
    private Integer gender;

    /** 启停状态(SysStatus 枚举本体: 1=ENABLED 启用, 0=DISABLED 停用); 停用后登录接口直接返回账号被禁用, 不允许进入系统. */
    private SysStatus status;

    /** 最近登录时间(Asia/Shanghai 时区, 登录成功时由 RbacServiceImpl.login 更新); 超过 90 天未登录触发自动停用流程(status -> DISABLED, 需运营重新激活). */
    private LocalDateTime lastLoginAt;

    private String remark;

    @TableLogic
    private Integer deleted = 0;

    /** 记录创建时间(Asia/Shanghai 时区, INSERT 时由 MetaObjectHandler 自动填充 now()). */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 记录更新时间(Asia/Shanghai 时区, INSERT/UPDATE 时由 MetaObjectHandler 自动填充 now()). */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建人用户名(从登录上下文取 sys_user.username, INSERT 时 MetaObjectHandler 自动填充); 批量任务/定时任务无上下文时填 "system". */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /** 更新人用户名(从登录上下文取 sys_user.username, INSERT/UPDATE 时 MetaObjectHandler 自动填充); 批量任务/定时任务无上下文时填 "system". */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 逻辑删除执行时间(Asia/Shanghai 时区); 仅 BaseServiceImpl.removeById 逻辑删除时显式写入, 正常查询此值为 NULL. */
    private LocalDateTime deleteAt;

    /** 逻辑删除执行人用户名; 仅 BaseServiceImpl.removeById 逻辑删除时显式写入, 正常查询此值为 NULL. */
    private String deleteBy;
}
