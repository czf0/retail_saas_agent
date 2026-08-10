package com.retail.rbac.dto.req;

import lombok.Data;

/**
 * 菜单新增请求.
 */
@Data
public class MenuCreateReq {

    private String menuName;

    /** 父菜单ID,0=根 */
    private Long parentId = 0L;

    /** M目录 C菜单 F按钮 */
    private Integer menuType;

    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer orderNum;

    /** 1显示 0隐藏 */
    private Integer visible;
}
