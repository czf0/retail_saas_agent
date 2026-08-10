package com.retail.business.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 商品规格定义请求(创建/更新通用).
 */
@Data
public class ProductSpecReq {

    /** 商品ID */
    private Long productId;

    /** 规格名,如"颜色"/"尺寸" */
    private String specName;

    /** 规格值数组,如 ["红","蓝"] */
    private List<String> specValues;

    /** 排序值,越小越靠前 */
    private Integer sortOrder;
}
