package com.retail.core.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.retail.core.config.props.StoreProperties;
import com.retail.core.security.LoginUser;
import com.retail.core.security.LoginUserHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

/**
 * 门店隔离处理器.
 * <p>
 * 复用 MyBatis-Plus {@link TenantLineHandler} 机制(第二个 {@code TenantLineInnerInterceptor} 实例),
 * 对 {@link StoreProperties#getStoreTableList()} 白名单中的表自动注入 {@code store_id} 查询条件与插入值.
 * <p>
 * <b>与 {@link TenantInterceptor} 的关键差异</b>:
 * <ul>
 *   <li>tenant_id 为空时抛 {@code TenantException}(租户必须存在);store_id 为空时在 {@link #ignoreTable(String)}
 *       阶段直接跳过该表,避免 MyBatis-Plus 把 null Expression 转成 {@code store_id = null} 字面量导致永不匹配.</li>
 *   <li>tenant 用黑名单(ignore-tables);store 用白名单(tables),仅白名单内表参与隔离.</li>
 *   <li>store_id 是 BIGINT,用 {@link LongValue} 生成无引号数字字面量.</li>
 * </ul>
 * <p>
 * <b>storeId 来源链路</b>:{@code LoginUser.storeId} → {@code GlobalReqInterceptor} 填充
 * {@code TenantContext.storeId}(String,null 时兜底空串)→ 本处理器读取.
 * <p>
 * <b>B-21 修复(联调发现)</b>:原实现仅靠 {@link #getTenantId()} 返回 null 期望 MyBatis-Plus 跳过,
 * 但 MyBatis-Plus 3.5.x 的 {@code TenantLineInnerInterceptor} 仍会生成 {@code store_id = null} 字面量,
 * 导致 tenant1_admin(无固定门店)查 order_info/product_stock 等门店隔离表时全部返回 0 条.
 * 改为在 {@link #ignoreTable(String)} 阶段判断:当前用户无门店归属(平台管理员 / 租户管理员 / 未登录 / 批量任务)
 * 时直接返回 true(忽略本表),让租户管理员能看到本租户全部门店数据,符合业务闭环预期.
 */
@Slf4j
@Component
public class StoreLineHandler implements TenantLineHandler {

    private final StoreProperties storeProperties;

    public StoreLineHandler(StoreProperties storeProperties) {
        this.storeProperties = storeProperties;
    }

    /**
     * 返回当前门店 ID.
     * <p>
     * 仅当 {@link #ignoreTable(String)} 返回 false(即白名单内表且当前用户有固定门店)时才会被调用,
     * 此处 currentStore 必为非空数字字符串.保留 null 兜底以防御异常上下文.
     */
    @Override
    public Expression getTenantId() {
        String currentStore = TenantContext.getStoreId();
        if (currentStore == null || currentStore.isEmpty()) {
            return null;
        }
        try {
            Long storeId = Long.parseLong(currentStore);
            // DEBUG 级:当前生效的门店隔离值(仅白名单内表 + 有门店用户会触发)
            log.debug("storeLineHandler getTenantId storeId={}", storeId);
            return new LongValue(storeId);
        } catch (NumberFormatException e) {
            log.warn("storeLineHandler 解析 storeId 失败 currentStore={} 回退为忽略", currentStore);
            return null;
        }
    }

    @Override
    public String getTenantIdColumn() {
        return storeProperties.getColumn();
    }

    /**
     * 白名单语义 + 用户门店归属双重判断.
     * <p>
     * 返回 true 表示忽略本表(不注入 store_id 条件);返回 false 表示生效(注入 store_id = ?).
     * <ul>
     *   <li>不在 {@code store.tables} 白名单中的表:忽略(保持原白名单语义).</li>
     *   <li>白名单内表,但当前登录用户无固定门店(平台管理员 / 租户管理员 / 未登录 / 批量任务):
     *       忽略,避免 B-21 的 {@code store_id = null} 字面量陷阱,租户管理员可见本租户全部门店数据.</li>
     *   <li>白名单内表且用户有固定门店(store_manager / store_staff):生效,注入 store_id 隔离本门店数据.</li>
     * </ul>
     * 新增门店级表只需在 application.yml 的 {@code store.tables} 追加.
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 第一层:白名单判断,不在白名单的表直接忽略
        if (!storeProperties.getStoreTableList().contains(tableName)) {
            return true;
        }
        // 第二层:B-21 修复,白名单内表若当前用户无门店归属则忽略,避免 store_id = null 字面量陷阱
        LoginUser user = LoginUserHolder.get();
        if (user == null || user.getStoreId() == null) {
            log.debug("storeLineHandler 忽略白名单表 tableName={} 原因=用户无门店归属（平台管理员/租户管理员）", tableName);
            return true;
        }
        log.debug("storeLineHandler 生效 tableName={} storeId={}", tableName, user.getStoreId());
        return false;
    }
}
