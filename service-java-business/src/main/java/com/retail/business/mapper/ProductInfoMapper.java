package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.ProductInfo;

/**
 * 商品主档 Mapper, 对应 product_info 表.
 * <p>多租户 + 门店隔离; tenant_id 与 store_id 由拦截器注入.
 * <p>基础 CRUD 由 BaseMapper 提供; 商品定位/检索条件在 Service 层用 LambdaQueryWrapper 构建.
 */
public interface ProductInfoMapper extends BaseMapper<ProductInfo> {
}