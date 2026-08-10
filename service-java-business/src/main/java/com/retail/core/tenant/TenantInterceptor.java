package com.retail.core.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.retail.core.config.props.TenantProperties;
import com.retail.core.exception.TenantException;
import com.retail.core.security.LoginUserHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 租户隔离处理器(MyBatis-Plus TenantLineHandler 实现).
 * <p>
 * 对非 ignore-tables 的表自动注入 {@code tenant_id} 查询条件与插入值.
 * <p>
 * <b>平台管理员跳过过滤</b>:{@link LoginUserHolder#isPlatformAdmin()} 返回 true 时
 * (tenantId 为 null 的超管账号),在 {@link #ignoreTable(String)} 阶段返回 true 跳过该表,
 * 使 admin 可跨租户查看全部业务数据.
 * <p>
 * <b>B-13 升级修复(联调发现)</b>:原修复仅在 {@link #getTenantId()} 返回 null 期望 MyBatis-Plus 跳过,
 * 但实测 MyBatis-Plus 3.5.x 的 {@code TenantLineInnerInterceptor} 仍会生成 {@code tenant_id = NULL}
 * 字面量(与 B-21 store_id 同模式),导致 admin 查询业务表全部返回 0 条.改为在
 * {@link #ignoreTable(String)} 阶段直接判断:平台管理员对所有非 ignore 表也返回 true(跳过),
 * 避免生成 {@code tenant_id = NULL} 永不匹配的字面量.普通用户走原逻辑:必须存在 tenantId 上下文.
 */
@Component
public class TenantInterceptor implements TenantLineHandler {

    private final TenantProperties tenantProperties;

    public TenantInterceptor(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public Expression getTenantId() {
        // B-28: 优先读 TenantContext(admin 操作租户级订单时由 Service 层注入),使后续 stock_movement 等
        // INSERT 正确带上 tenant_id,避免 NOT NULL 约束违反
        String currentTenant = TenantContext.getTenantId();
        if (currentTenant != null && !currentTenant.isEmpty()) {
            return new StringValue(currentTenant);
        }
        // 平台管理员无显式租户上下文,且在 ignoreTable 阶段已跳过;防御性返回 null
        if (LoginUserHolder.isPlatformAdmin()) {
            return null;
        }
        if (currentTenant == null || currentTenant.isEmpty()) {
            // 普通用户必须有租户上下文,否则视为非法请求
            throw new TenantException();
        }
        return new StringValue(currentTenant);
    }

    @Override
    public String getTenantIdColumn() {
        return tenantProperties.getColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        List<String> ignoreList = tenantProperties.getIgnoreTableList();
        // 第一层:黑名单判断,ignore-tables 中的表(RBAC/sys_config 等平台级表)直接忽略
        if (ignoreList.contains(tableName)) {
            return true;
        }
        // B-28: admin 操作租户级订单时,Service 层会注入 TenantContext(订单的 tenant_id),
        // 此时不再跳过该表,使 tenant_id 正确注入 INSERT 与 SELECT 过滤
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !contextTenant.isEmpty()) {
            return false;
        }
        // 第二层:B-13 升级修复,平台管理员(无显式上下文)跳过所有表的 tenant_id 过滤,
        // 避免 tenant_id = NULL 字面量陷阱,使 admin 可跨租户查看全部业务数据
        if (LoginUserHolder.isPlatformAdmin()) {
            return true;
        }
        return false;
    }
}
