package com.retail.rbac.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 登录后用户权限信息响应(getInfo 接口);内嵌当前登录 UserInfo + 角色 key 字符串列表 + 权限标识字符串列表(Sa-Token 权限计算前端映射用).
 * <p>Controller: GET /api/v1/auth/getInfo;每次前端 router beforeEach 路由守卫调用;响应可本地缓存 10min,登出时清缓存.
 */
@Data
public class UserPermResp {

    /** 内嵌当前登录用户基本信息(同 UserInfo;侧边栏/头像区用). */
    private UserInfo user;

    /** 角色 key 列表(对应 sys_role.role_key;如 ["STORE_MANAGER","FINANCE"];前端 v-hasRole 指令入参;内置超级管理员含 "admin"). */
    private List<String> roles;

    /** 权限标识列表(sys_menu.perms;如 ["system:user:add","product:edit"];前端 v-hasPermi 指令入参;超管简化为 ["*"],不展开全量以节省带宽). */
    private List<String> permissions;
}
