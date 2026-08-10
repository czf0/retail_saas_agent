package com.retail.rbac.convert;

import com.retail.core.convert.BaseMapStructConfig;
import com.retail.core.convert.ReqConvert;
import com.retail.core.convert.RespConvert;
import com.retail.rbac.dto.req.MenuCreateReq;
import com.retail.rbac.dto.resp.MenuResp;
import com.retail.rbac.entity.SysMenu;
import org.mapstruct.Mapper;

/**
 * 菜单 转换器.
 * <p>toResp/toRespList 由 {@link RespConvert} 提供;请求转换 {@code MenuCreateReq→SysMenu} 由 {@link ReqConvert} 提供.
 * 菜单树(MenuTreeResp)由 Service 基于 MenuResp 列表自行组装,不经转换器.
 */
@Mapper(config = BaseMapStructConfig.class)
public interface MenuConvert extends RespConvert<SysMenu, MenuResp>, ReqConvert<MenuCreateReq, SysMenu> {
}
