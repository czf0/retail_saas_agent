package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商品 SKU 列表响应.
 * <p>
 * productId / productName 由 Service 层查询 product_info 后填充(SKU 列表按单个 productId 过滤,
 * 所有行归属同一商品,单次 selectById 比 JOIN 更高效且避免 specJson JSON TypeHandler 在自定义 SQL 中的复杂性).
 * specJson / cost / createdAt 等同名字段由 MapStruct 自动映射.
 */
@Data
public class ProductSkuListItemResp {

    private Long id;

    /** 商品ID(MapStruct 同名自动映射) */
    private Long productId;

    /** 商品名称(Service 层填充,消除前端数据孤岛) */
    private String productName;

    /** SKU编码 */
    private String skuCode;

    /** SKU名称 */
    private String skuName;

    /** 规格键值对(MapStruct 同名自动映射,JacksonTypeHandler 由实体 autoResultMap 处理) */
    private Map<String, String> specJson;

    /** SKU售价 */
    private BigDecimal price;

    /** SKU成本(MapStruct 同名自动映射) */
    private BigDecimal cost;

    /** 库存数量 */
    private Integer stockQty;

    /** 上下架状态:on_shelf / off_shelf */
    private Integer status;

    /** 创建时间(MapStruct 同名自动映射) */
    private LocalDateTime createdAt;
}
