package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档 Mapper (MyBatis-Plus BaseMapper 提供 CRUD, 无需 XML).
 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
