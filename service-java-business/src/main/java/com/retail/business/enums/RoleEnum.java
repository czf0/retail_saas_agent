package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 业务端角色枚举(与 RBAC 权限角色体系分离; 仅用于业务域身份打标记).
 * <p>业务域角色标记, 请勿与 RBAC 权限系统角色混淆(后者使用 RoleKeyConst + sys_role 表):
 * <ul>
 *   <li>ADMIN(1 管理员): 平台管理员级业务操作标记; 用于日志归属, 不作为权限门禁.</li>
 *   <li>TENANT_USER(2 租户用户): 普通租户用户业务操作标记; 所有业务端用户默认角色.</li>
 * </ul>
 * 
 * @see com.retail.rbac.enums.RoleKeyConst
 */
public enum RoleEnum implements BaseEnum {

    /** 管理员级业务身份标记; 在 op_log.created_by 中打标用于审计追溯; 不授予 RBAC 权限 - 权限门禁请使用 RoleKeyConst.SUPER_ADMIN. */
    ADMIN(1, "管理员"),
    /** 普通租户用户身份标记; 业务端用户动作默认值; 实际权限由 sys_user_role + sys_role_menu 关联决定. */
    TENANT_USER(2, "租户用户");

    @EnumValue
    private final Integer code;
    private final String desc;

    RoleEnum(Integer code, String desc) {
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
