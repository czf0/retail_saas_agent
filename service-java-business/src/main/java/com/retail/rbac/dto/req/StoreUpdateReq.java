package com.retail.rbac.dto.req;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店修改请求(部分更新,null 字段不更新).
 * 业务扩展字段支持部分更新,null 表示不修改.
 */
@Data
public class StoreUpdateReq {

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

    private Integer status;
}
