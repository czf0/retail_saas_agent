package com.retail.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.retail.business.entity.PointsLog;

/**
 * 会员积分流水 Mapper. 
 * <p>基础 CRUD 由 BaseMapper 提供; 积分变动逻辑由 PointsServiceImpl 在 Service 层封装, 
 * 保证流水写入与 member.points 更新同事务. 
 */
public interface PointsLogMapper extends BaseMapper<PointsLog> {
}
