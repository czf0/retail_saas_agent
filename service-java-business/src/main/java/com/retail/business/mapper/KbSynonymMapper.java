package com.retail.business.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.KbSynonym;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同义词 Mapper (MyBatis-Plus BaseMapper 提供 CRUD).
 * <p>
 * 本表 scope=global 时 tenant_id=NULL (跨租户通用), 用 @InterceptorIgnore 关闭多租户拦截,
 * 在 Service 层手动按 scope+tenant_id 过滤 (与 sys_user 同模式, 无需改 application.yml ignore-tables).
 * tenantLine = "true" 表示忽略租户拦截器, 不自动注入 tenant_id 条件.
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KbSynonymMapper extends BaseMapper<KbSynonym> {
}
