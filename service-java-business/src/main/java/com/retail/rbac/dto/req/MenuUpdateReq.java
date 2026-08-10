package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 菜单修改请求.
 */
@Data
public class MenuUpdateReq {

    private String menuName;

    private Long parentId;

    private Integer menuType;

    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer orderNum;

    private Integer visible;

    private Integer status;
}
