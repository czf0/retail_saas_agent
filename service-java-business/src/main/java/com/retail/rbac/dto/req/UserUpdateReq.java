package com.retail.rbac.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 用户修改请求(部分更新,null 字段不更新).
 */
@Data
public class UserUpdateReq {

    private String nickName;

    private String email;

    private String phone;

    private Integer gender;

    private Integer status;

    private String remark;

    /** 门店ID(可空,null=无固定门店) */
    private Long storeId;

    /** 修改时重新分配的角色ID列表(非 null 才更新) */
    private List<Long> roleIds;
}
