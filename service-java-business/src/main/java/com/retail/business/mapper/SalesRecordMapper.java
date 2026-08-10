package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.SalesRecord;

/**
 * 销售记录 Mapper, 对应 sales_record 表.
 * <p>多租户 + 门店隔离; tenant_id 与 store_id 由拦截器注入.
 * <p>基础 CRUD 由 BaseMapper 提供; 销售明细条件查询在 Service 层用 LambdaQueryWrapper 构建.
 */
public interface SalesRecordMapper extends BaseMapper<SalesRecord> {
}