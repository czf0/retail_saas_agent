package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SKU Mapper. 
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {
}
