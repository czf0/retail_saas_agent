package com.retail.business.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 商品规格定义详情/列表行项(商品详情页规格选项渲染 + 后台规格编辑回显);同一 SPU 可多规格(颜色/尺码),组合生成 SKU 笛卡尔积.
 * <p>内嵌子对象 ProductSpecResp.specValues = 该规格项下可选值列表(前端规格圆点/下拉数据来源).
 */
@Data
public class ProductSpecResp {

    private Long id;

    /** 所属 SPU 外键(product_info.id). */
    private Long productId;

    /** 规格项名(如"颜色"/"尺码";前端 tab 名). */
    private String specName;

    /** 规格值集合(1:N,同一 SPU 同规格名下的可选值列表;如颜色=["红色","蓝色"]). */
    private List<String> specValues;

    /** 同级规格项显示排序(升序 ASC;尺码在颜色之后). */
    private Integer sortOrder;
}
