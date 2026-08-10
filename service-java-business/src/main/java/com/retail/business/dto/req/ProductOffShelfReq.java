package com.retail.business.dto.req;

import lombok.Data;

/**
 * 商品下架请求体, 运营后台商品管理 -> 单商品下架时前端对话框提交的下架原因.
 * <p>对应 Controller 路由: POST /api/v1/products/{productId:\d+}/off-shelf; 复用批量下架 Service 包装单条 productIds.
 * <p>reason 可选(下架原因, 记录到审计/流水), 不校验必填.
 */
@Data
public class ProductOffShelfReq {

    /** 下架原因(可选), 供审计追溯与批量下架原因透传. */
    private String reason;
}
