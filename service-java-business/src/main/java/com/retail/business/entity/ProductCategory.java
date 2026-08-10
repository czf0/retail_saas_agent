package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import com.retail.business.enums.CategoryStatus;

import java.time.LocalDateTime;

/**
 * 商品分类实体, 对应数据库 product_category 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(分类为租户全局统一配置).
 * <p>树形结构约束: parentId = 0 表示根节点; 其他值指向父分类 product_category.id; 层级深度 max = 3(Service 创建时校验, 超限抛 ParamException).
 * <p>唯一约束: UNIQUE(tenant_id, parent_id, name), 同层下分类名不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName("product_category")
public class ProductCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父分类 id, 指向 product_category.id; 根节点固定 = 0; 查询树时 Service 层递归组装; 删除父节点时需要级联移动其下子节点到新父节点(不可直接删除有子分类的节点). */
    private Long parentId;

    private String name;

    /** 同级排序值; 数值越小越靠前(ASC 升序); 根分类推荐间隔 100 插入(100 / 200 / 300), 预留中间值便于后续运营调整顺序. */
    private Integer sortOrder;

    /** 分类启停状态(CategoryStatus 枚举本体: 1=ENABLED 启用, POS/商品列表可展示; 0=DISABLED 停用, 不展示, 不级联影响商品自身 status). */
    private CategoryStatus status;

    private String description;

    private Long tenantId;
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
