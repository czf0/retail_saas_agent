package com.retail.rbac.satoken;

import com.retail.rbac.enums.DataScope;
import com.retail.rbac.enums.RoleKeyConst;
import com.retail.rbac.mapper.SysRoleMapper;
import com.retail.core.context.AuditUserContext;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import org.springframework.stereotype.Component;

/**
 * 数据权限范围辅助工具.
 * <p>
 * 基于角色 {@code data_scope} 字段判断当前用户是否需要行级数据过滤:
 * <ul>
 *   <li>{@link DataScope#ALL}(默认):不附加条件,可见租户内全部数据(租户隔离已由 TenantLineInterceptor 处理)</li>
 *   <li>{@link DataScope#SELF}:仅可见自己创建的数据(附加 {@code create_by = 当前用户名} 条件)</li>
 * </ul>
 * <p>
 * <b>最广范围优先</b>:用户拥有多个角色时,取最小 data_scope(最广范围)生效.
 * 超级管理员始终为 ALL,不附加过滤.
 * <p>
 * <b>使用方式</b>(在 Service 层查询方法中调用):
 * <pre>{@code
 * LambdaQueryWrapper<Entity> wrapper = new LambdaQueryWrapper<>();
 * // ... 其他条件 ...
 * if (dataScopeHelper.needSelfScope()) {
 *     wrapper.eq(Entity::getCreateBy, dataScopeHelper.currentOperator());
 * }
 * }</pre>
 * <p>
 * 仅适用于含 {@code create_by} 字段的实体(逻辑删除表);关系表(sys_user_role 等)和
 * 快照表(sales_record 等)不适用.
 */
@Component
public class DataScopeHelper {

    private final SysRoleMapper sysRoleMapper;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试 */
    public DataScopeHelper(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    /**
     * 判断当前登录用户是否需要 SELF 数据范围过滤.
     * <p>
     * 超管 → false;ALL 角色 → false;仅 SELF 角色 → true;未登录 → false(无过滤条件可附加).
     *
     * @return true 表示需要按 create_by 过滤仅本人数据
     */
    public boolean needSelfScope() {
        LoginUser lu = LoginUserHolder.get();
        if (lu == null || lu.getUserId() == null) {
            return false;
        }
        // 超级管理员:全部数据,不附加过滤
        if (lu.getRoleKeys() != null && lu.getRoleKeys().stream().anyMatch(RoleKeyConst.SUPER_ADMIN::equals)) {
            return false;
        }
        // 查询用户角色的最广 data_scope
        Integer minScope = sysRoleMapper.selectMinDataScopeByUserId(lu.getUserId());
        if (minScope == null) {
            // 无有效角色,保守起见不附加过滤(由权限注解拦截无权访问)
            return false;
        }
        // ALL(1) → false; SELF(5) → true
        return minScope >= DataScope.SELF.getCode();
    }

    /**
     * 获取当前操作人标识(用于 create_by 过滤条件).
     * <p>
     * 委托 {@link AuditUserContext#currentUser()},确保与 {@code createBy} 字段自动填充值完全一致
     * ({@code createBy} 由 {@code AuditMetaObjectHandler.insertFill} 调用同一方法填充),
     * 避免出现「填充值与过滤值口径不一致导致 SELF 过滤命中 0 行」的问题.
     *
     * @return 当前操作人用户名(未登录回退 "system")
     */
    public String currentOperator() {
        return AuditUserContext.currentUser();
    }
}
