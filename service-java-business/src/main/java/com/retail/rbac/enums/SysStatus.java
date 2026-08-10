package com.retail.rbac.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * RBAC 通用启停状态枚举; 用于 SysMenu / SysRole / SysStore / SysUser 的 status 字段.
 * <p>RBAC 实体通用开关状态; code = 1 启用 / 0 停用; 默认列表查询追加 status=1 WHERE 条件过滤掉停用实体:
 * <ul>
 *   <li>ENABLED(1 启用): 实体生效; SysUser 可登录; SysMenu 在侧边栏展示; SysRole 可分配; SysStore 默认查询纳入数据.</li>
 *   <li>DISABLED(0 停用): 实体失效; SysUser 登录拒绝返回错误码; SysMenu 侧边栏隐藏; SysRole 不可再分配; SysStore 默认列表查询过滤掉.</li>
 * </ul>
 */
public enum SysStatus implements BaseEnum {

    /** 启用; 账号可登录, 菜单侧边栏展示, 角色可被分配, 门店默认列表查询纳入结果; 启停开关的默认值. */
    ENABLED(1, "启用"),
    /** 停用; 账号无法登录报错, 菜单侧边栏隐藏, 角色不可分配, 门店数据从默认列表中过滤; 不做物理级联删除. */
    DISABLED(0, "停用");

    @EnumValue
    private final Integer code;
    private final String desc;

    SysStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
