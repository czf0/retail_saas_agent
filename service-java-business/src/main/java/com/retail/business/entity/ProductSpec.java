package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品规格定义实体, 对应数据库 product_spec 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(规格为租户全局 SPU 维度配置).
 * <p>业务约束: 一个 SPU 可定义多个规格维度(如 "颜色" + "尺寸" 两个维度), 每个维度 specValues 为 JSON 数组存储可选值; 最终 SKU 列表 = 各维度笛卡尔积(颜色 3 种 × 尺寸 4 种 = 12 个 SKU, Service 层生成).
 * <p>顺序约束: UNIQUE(product_id, sortOrder), 同一 SPU 下排序号不可重复; 前端展示顺序按 sortOrder ASC(如先选颜色再选尺码的购买习惯).
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName(value = "product_spec", autoResultMap = true)
public class ProductSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 所属 SPU 商品 id, 指向 product_info.id; 一个 SPU 可挂多个规格维度(通常 1-3 个, 过多维度笛卡尔积爆炸, Service 层限制 max=5). */
    private Long productId;

    /** 规格维度名称(如 "颜色"/"尺码"/"材质"/"容量"); 前端商品详情页规格选择卡标题展示用, 建议 2-6 个汉字简洁明了. */
    private String specName;

    /** 规格可选值列表(JSON 数组, 如 ["红色","蓝色","黑色"]); 注意: 值列表变动会影响已有 SKU(新增值需补生成 SKU, 删除值需校验是否有关联 SKU, 有则禁止删除). */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> specValues;

    /** 同级排序值(同一 SPU 内各维度比较); 数值越小越靠前(ASC 升序); 典型电商: 100=颜色, 200=尺码(先选色再选码的购买流程). */
    private Integer sortOrder;
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
