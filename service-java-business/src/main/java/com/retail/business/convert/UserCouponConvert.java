package com.retail.business.convert;

import com.retail.business.dto.resp.UserCouponListItemResp;
import com.retail.business.dto.resp.UserCouponResp;
import com.retail.business.entity.UserCoupon;
import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.RespConvert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 用户优惠券 转换器.
 * <p>主转换 {@code UserCoupon→UserCouponResp} 由 {@link RespConvert} 提供(toResp/toRespList);
 * 次级目标 UserCouponListItemResp 为同名字段,声明命名方法即可.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface UserCouponConvert extends RespConvert<UserCoupon, UserCouponResp> {

    /** 用户优惠券列表项 */
    UserCouponListItemResp toListItem(UserCoupon entity);

    /** 批量列表 */
    List<UserCouponListItemResp> toListItemList(List<UserCoupon> entities);
}
