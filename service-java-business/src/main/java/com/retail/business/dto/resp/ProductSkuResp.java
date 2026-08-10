package com.retail.business.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 商品 SKU 详情响应;包含规格键值对 + 独立售价/成本/库存 + 上下架状态(详情页前端切换规格动态刷新当前 SKU 信息).
 * <p>Controller: GET /api/v1/products/{productId:\\d+}/skus/{skuId:\\d+};或 ProductResp 内嵌子对象列表.
 */
@Data
public class ProductSkuResp {

    private Long id;

    /** 所属 SPU 外键(product_info.id). */
    private Long productId;

    /** SKU 编码(业务唯一键;条码扫描入库用;对接 ERP 用). */
    private String skuCode;

    /** SKU 名称(规格组合名,如"红色-XL";前端规格区选中展示). */
    private String skuName;

    /** 规格键值对(Map,key=规格项如"颜色"/"尺码",value=规格值如"红色"/"XL");用于前端规格圆点渲染与库存回查. */
    private Map<String, String> specJson;

    /** SKU 售价(单位: 元,精度: 分;前台 PDP 价格=当前选中 SKU 的 price). */
    private BigDecimal price;

    /** SKU 成本价(单位: 元,精度: 分;仅后台管理端展示,前端不返回;毛利率计算基础). */
    private BigDecimal cost;

    /** 可用库存数量(product_stock 汇总;正整数;可卖数 = stockQty - reservedQty,此处=可卖数简化). */
    private Integer stockQty;

    /** SKU 上下架状态:1=ON_SHELF(在售) 0=OFF_SHELF(停售);独立于 SPU 的 status,SPU 下架时所有 SKU 强制无效. */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间(规格/价格变更会更新,前端可判断是否刷新缓存). */
    private LocalDateTime updatedAt;
}
