package com.retail.rbac.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * RBAC 角色数据权限范围枚举; sys_role.data_scope 列.
 * <p>SQL 过滤条件由 DataScopeHelper.getCondition() 统一生成; Service 层直接追加 WHERE, 禁止散落手工编写. code = 1 全部数据（平台超管看全部租户） 2 自定义（通过 role_store 关联门店） 3 本门店 4 本部门及以下 5 仅本人:
 * <ul>
 *   <li>ALL(1 全部数据): 不追加 WHERE 范围条件; 仅限平台级超管; 可看全部租户全部门店全部数据.</li>
 *   <li>CUSTOM(2 自定义数据权限): 通过 sys_role_store 关联表定义门店白名单; WHERE store_id IN（role_store 列表）; 管理员可选指定门店.</li>
 *   <li>OWN_STORE(3 本门店): WHERE store_id = 当前用户 store_id; 店长默认角色; 不可看同级兄弟门店.</li>
 *   <li>DEPT_AND_CHILD(4 本部门及以下): WHERE dept_id IN（用户部门 + 递归子部门）; 适用跨多门店的区域经理.</li>
 *   <li>SELF(5 仅本人数据): WHERE creator = 当前 user_id; 仅可看本人创建的记录; 初级销售角色默认值.</li>
 * </ul>
 */
public enum DataScope implements BaseEnum {

    /** 全部数据不设范围过滤; 仅限平台超管使用; 不追加 WHERE 条件; 无视创建者即可看全部租户全部门店全部数据. */
    ALL(1, "全部数据"),
    /** 自定义门店白名单范围; 通过 sys_role_store 关联表定义具体 store_ids 列表; WHERE store_id IN（role_store 选中列表）; 租户管理员可分配自定义门店列表. */
    CUSTOM(2, "自定义数据权限"),
    /** 仅当前门店范围; WHERE store_id = 当前用户默认 store_id; 店长默认角色; 即使同部门也不可查看兄弟门店. */
    OWN_STORE(3, "本门店"),
    /** 部门及子部门范围; WHERE dept_id IN（当前用户部门 + 递归子部门 dept_ids）; 覆盖多门店的区域经理角色. */
    DEPT_AND_CHILD(4, "本部门及以下"),
    /** 仅本人最小范围; WHERE creator_id = 当前 user_id; 仅看自己创建的记录; 销售见习 / 收银员等受限可见性默认角色. */
    SELF(5, "仅本人数据");

    @EnumValue
    private final Integer code;
    private final String desc;

    DataScope(Integer code, String desc) {
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
