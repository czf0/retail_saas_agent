package com.retail.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.rbac.enums.SysStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店实体, 对应数据库 sys_store 表.
 * <p>隔离域: 已配置在 ignore-tables 中(tenant_id 非空, 一租户多门店), tenant_id 由 MetaObjectHandler 插入时自动填充(租户管理员创建时取当前租户); 查询在 Service 层手动按 tenant_id 过滤(租户管理员仅查本租户门店, 平台管理员查全部).
 * <p>唯一约束: UNIQUE(tenant_id, store_code), 同一租户下门店编码不可重复; UNIQUE(tenant_id, store_name), 同一租户下门店名不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("sys_store")
public class SysStore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String storeName;

    /** 门店编码(业务唯一键, POS/单据打印展示; 建议规则: 租户缩写 + 3 位序号, 如 SH-001). */
    private String storeCode;

    private String address;

    private String phone;

    /** 营业时间(格式 "HH:mm-HH:mm", 多个时段逗号分隔如 "09:00-12:00,14:00-22:00"); Agent 推荐门店时判断当前是否营业中. */
    private String businessHours;

    /** 店长姓名(冗余快照, 避免门店列表查询 JOIN sys_user; 变更店长时同步更新此值). */
    private String managerName;

    /** 店长用户 id, 指向 sys_user.id; 店长登录后 StoreLineHandler 自动注入此 store_id 到 SQL 层过滤条件. */
    private Long managerId;

    /** 经度(DECIMAL(10,7), 精度到 1cm, WGS84 坐标系); 小程序端按距离排序门店时使用, 配合 latitude 计算 haversine 距离. */
    private BigDecimal longitude;

    /** 纬度(DECIMAL(10,7), 精度到 1cm, WGS84 坐标系); 与 longitude 配对使用, NULL = 未配置坐标, 不参与距离排序. */
    private BigDecimal latitude;

    private String remark;

    /** 启停状态(SysStatus 枚举本体: 1=ENABLED 启用, 0=DISABLED 停用); 停用后 POS 端不可选此门店下单, 库存调拨也排除. */
    private SysStatus status;

    @TableLogic
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
