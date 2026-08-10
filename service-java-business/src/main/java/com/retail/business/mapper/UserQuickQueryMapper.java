package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.UserQuickQuery;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户快捷提问 Mapper (MyBatis-Plus BaseMapper 提供 CRUD).
 */
@Mapper
public interface UserQuickQueryMapper extends BaseMapper<UserQuickQuery> {
}
