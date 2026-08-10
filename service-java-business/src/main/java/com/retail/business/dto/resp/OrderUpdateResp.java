package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 订单修改操作结果响应(修改收货地址/备注/门店等非金额字段);返回是否成功 + 受影响行数(乐观锁校验).
 * <p>注意:金额/商品项变更走专用"订单改价/订单加项"接口,不走此通用 Update 接口.
 */
@Data
public class OrderUpdateResp {

    /** true = 订单状态允许修改(如 PENDING/PAID)且版本号匹配;false = 状态机校验不通过/数据被他人已改. */
    private Boolean success;

    /** 操作提示信息;失败时包含具体原因如"订单已发货,不可修改地址". */
    private String message;

    /** 受影响行数(乐观锁 WHERE id=#id AND version=#version;=1 表示成功更新;=0 表示版本冲突已被他人抢占更新). */
    private Long updated;
}
