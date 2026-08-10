package com.retail.rbac.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店新增请求.
 * <p>tenantId 由拦截器自动注入(租户管理员创建本租户门店);平台管理员跨租户创建时显式传入 tenantId.
 * 业务扩展字段(address/phone/businessHours/managerName/managerId/经纬度/remark)均为可选.
 */
@Data
public class StoreCreateReq {

    /** 平台管理员跨租户创建时显式指定;租户管理员创建时留空(自动取当前租户) */
    private Long tenantId;

    private String storeName;

    private String storeCode;

    /** 门店地址 */
    private String address;

    /** 联系电话 */
    private String phone;

    /** 营业时间,如 "09:00-22:00" */
    private String businessHours;

    /** 店长姓名(冗余便于展示) */
    private String managerName;

    /** 店长用户ID,关联 sys_user.id */
    private Long managerId;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 备注 */
    private String remark;
}
