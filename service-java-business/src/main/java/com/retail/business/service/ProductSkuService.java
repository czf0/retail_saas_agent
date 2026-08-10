package com.retail.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.retail.business.dto.OperationResultResp;
import com.retail.business.dto.req.ProductSkuCreateReq;
import com.retail.business.dto.req.ProductSkuUpdateReq;
import com.retail.business.dto.resp.ProductSkuListItemResp;
import com.retail.business.dto.resp.ProductSkuResp;
import com.retail.business.entity.ProductSku;
import com.retail.core.dto.PageResp;

/**
 * 商品 SKU 服务.
 */
public interface ProductSkuService extends IService<ProductSku> {

    /** 创建 SKU:状态默认 on_shelf,返回创建后的 SKU 详情. */
    ProductSkuResp createSku(ProductSkuCreateReq req);

    /** 部分更新 SKU(skuName/price/cost/status/stockQty),返回更新后的 SKU 详情. */
    ProductSkuResp updateSku(Long skuId, ProductSkuUpdateReq req);

    /** 逻辑删除 SKU. */
    OperationResultResp deleteSku(Long skuId);

    /** SKU 详情. */
    ProductSkuResp getSku(Long skuId);

    /** 分页查询某商品的 SKU,支持 status / keyword 过滤. */
    PageResp<ProductSkuListItemResp> listSkus(Long productId, String status, String keyword);

    /** 按 SKU 编码查询. */
    ProductSkuResp getBySkuCode(String skuCode);
}
