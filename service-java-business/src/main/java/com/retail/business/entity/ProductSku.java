package com.retail.business.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import com.retail.business.enums.SkuStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商品 SKU(最小可售单元)实体, 对应数据库 product_sku 表.
 * <p>多租户隔离(tenant_id 由 TenantInterceptor 自动注入 WHERE 条件), 不进行门店隔离(SKU 为租户全局商品规格, 各门店共用同一 SKU 定义, 库存按门店拆分 product_stock).
 * <p>业务约束: SKU = 商品最小可售单元, 一个 SPU(product_info) 下挂 N 个 SKU; 单规格商品自动创建 1 条 SKU(specJson 为空 {} 或 NULL); specJson 为规格键值对(颜色/尺码等), 同一 SPU 下 specJson 组合不可重复(Service 层校验).
 * <p>唯一约束: UNIQUE(tenant_id, sku_code), 租户内 SKU 编码全局唯一; UNIQUE(product_id, spec_json_hash), 同 SPU 下规格组合不可重复.
 * <p>通用审计字段说明: See: {@link com.retail.rbac.entity.SysUser}.
 */
@Data
@TableName(value = "product_sku", autoResultMap = true)
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 所属 SPU 商品 id, 指向 product_info.id; SPU 删除(逻辑删)时级联逻辑删除其下所有 SKU(同事务). */
    private Long productId;

    /** SKU 编码(UNIQUE(tenant_id, sku_code), 租户内全局唯一); 生成规则: spu_code + "-" + 3 位序号(如 SP001-001); 仓库拣货 / 小票打印 / 扫码识别主码. */
    private String skuCode;

    /** SKU 展示名称(规格拼接, 如 "红色-XL/棉质"); 前端商品详情页规格选择展示用 + 购物车 / 订单明细展示用; 由 specJson 自动拼装生成(Service 层写入). */
    private String skuName;

    /** 规格键值对(Map<String,String> JSON 字段); key = 规格维度名("颜色"/"尺码"), value = 具体值("红色"/"XL"); 单规格商品为 {}(空 map 或 NULL, 不存储默认规格). */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> specJson;

    /** SKU 实际零售价(单位: 元, 精度: 分, DECIMAL(12,2)); POS / 小程序下单最终成交价取此值(优先级高于 product_info.price SPU 默认价). */
    private BigDecimal price;

    /** SKU 实际成本价(单位: 元, 精度: 分, DECIMAL(12,2)); 毛利报表 = (price - cost) * 销售件数, 采购入库更新此值(先进先出 / 加权平均, 由财务策略决定). */
    private BigDecimal cost;

    /** 冗余 SKU 全局总库存(汇总所有门店 product_stock.available_qty 之和); 异步更新(允许秒级延迟), 精确值以 product_stock(指定门店)为准; 商品详情页展示 "库存充足/库存紧张". */
    private Integer stockQty;

    /** SKU 上下架状态(SkuStatus 枚举本体: 1=ON_SHELF 上架可售, 0=OFF_SHELF 下架停售); 默认 ON_SHELF, 运营可手动下架某规格(如 "红色卖完了, 只卖蓝色"); 不级联影响 SPU status. */
    private SkuStatus status;
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
