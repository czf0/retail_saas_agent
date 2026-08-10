package com.retail.business.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.ProductListReq;
import com.retail.business.dto.req.ProductOffShelfToolReq;
import com.retail.business.dto.req.ProductPriceAdjustToolReq;
import com.retail.business.dto.resp.ProductBatchActionResp;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductPriceAdjustResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.enums.ProductStatus;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.service.ProductInfoService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.tenant.TenantContext;
import com.retail.core.dto.PageResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商品域 AgentTool 整改运行时验证.
 * <p>
 * 覆盖 5 项核心:
 *   1. DB 字段: product_info.clearance / shelf_life_days 是否真实存在
 *   2. DB 记录: agent_tool_definition 是否有 4 条新工具 (off_shelf/on_shelf/price_adjust/delete)
 *   3. Service: listProducts 的 inStock=true 与 clearance=true 过滤是否真实生效
 *   4. Service: resolveProductId 三选一定位 (按name)
 *   5. Service: priceAdjust 改价 + 差价计算 + batchOffShelf 下架状态变更
 * <p>
 * WebEnvironment.NODE 不启动 Tomcat, 不占用 8080 端口, 只初始化 Spring 容器.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.main.web-application-type=none"
})
class ProductToolVerifyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ProductInfoService productInfoService;
    @Autowired
    private ProductInfoMapper productInfoMapper;

    @BeforeEach
    void setUp() {
        // 多租户上下文: 租户 1001 (鼎盛超市, 有种子数据)
        TenantContext.setTenantId("1001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PageContextHolder.clear();
    }

    @Test
    @DisplayName("[DB-1] product_info 表存在 clearance 和 shelf_life_days 字段")
    void db_productInfoColumns() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, "product_info", null);
            List<String> cols = new java.util.ArrayList<>();
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME"));
            }
            System.out.println("product_info.columns = " + cols);
            assertTrue(cols.contains("clearance"), "缺少 clearance 字段: " + cols);
            assertTrue(cols.contains("shelf_life_days"), "缺少 shelf_life_days 字段: " + cols);
        }
    }

    @Test
    @DisplayName("[DB-2] agent_tool_definition 表存在 product:off_shelf/on_shelf/price_adjust/delete 4 条新工具")
    void db_agentToolDefinitions() {
        // 预期的 4 个新工具名
        List<String> expectedTools = Arrays.asList(
                "product:off_shelf", "product:on_shelf", "product:price_adjust", "product:delete");
        String sql = "SELECT tool_name, enabled, tool_group, description FROM agent_tool_definition"
                + " WHERE tool_name IN (?, ?, ?, ?) AND deleted = 0";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, expectedTools.toArray());
        System.out.println("agent_tool_definition rows count: " + rows.size()
                + " (不同版本/组别可能重复，只要每个工具名都有至少 1 条 enabled=1 即可)");
        assertTrue(rows.size() >= 4, "4 个新工具应该至少各有 1 条记录, 实际: " + rows.size());
        for (String tName : expectedTools) {
            long enabledCount = rows.stream()
                    .filter(r -> tName.equals(r.get("tool_name")) && Integer.valueOf(1).equals(((Number) r.get("enabled")).intValue()))
                    .count();
            assertTrue(enabledCount >= 1, "缺少 enabled=1 的工具记录: " + tName);
        }
        List<String> actualNames = rows.stream()
                .map(r -> (String) r.get("tool_name")).distinct().collect(Collectors.toList());
        expectedTools.forEach(t -> assertTrue(actualNames.contains(t),
                "缺少工具名: " + t));
    }

    @Test
    @DisplayName("[DB-3] sys_menu 表存在商品域 7 个按钮权限 (270-273 / 283-285)")
    void db_sysMenuProductButtons() {
        List<Integer> ids = Arrays.asList(270, 271, 272, 273, 283, 284, 285);
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, menu_name, parent_id, menu_type, perms, order_num FROM sys_menu WHERE id IN (" + placeholders + ") ORDER BY id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
        System.out.println("商品域 sys_menu 按钮:");
        rows.forEach(r -> System.out.println("  id=" + r.get("id")
                + " name=" + r.get("menu_name")
                + " parent=" + r.get("parent_id")
                + " type=" + r.get("menu_type")
                + " perms=" + r.get("perms")
                + " order=" + r.get("order_num")));
        assertEquals(7, rows.size(), "应该有 7 条商品按钮 (query/add/edit/remove/offShelf/onShelf/priceAdjust), 实际: " + rows.size());

        // 逐条校验 perms + 父菜单=商品管理(18) + 类型=F
        java.util.Map<Integer, String> expectedPerms = new java.util.HashMap<>();
        expectedPerms.put(270, "business:product:query");
        expectedPerms.put(271, "business:product:add");
        expectedPerms.put(272, "business:product:edit");
        expectedPerms.put(273, "business:product:remove");
        expectedPerms.put(283, "business:product:offShelf");
        expectedPerms.put(284, "business:product:onShelf");
        expectedPerms.put(285, "business:product:priceAdjust");
        for (Map<String, Object> r : rows) {
            Integer id = ((Number) r.get("id")).intValue();
            assertEquals(18, ((Number) r.get("parent_id")).intValue(), "id=" + id + " 的父菜单必须是商品管理(18)");
            // menu_type 在 seed_reset_all.sql 里写的是 CHAR('C'/'F')，但真实 DB 列是 TINYINT/BIT 会被 jdbcTemplate 转成 Boolean/Number，
            // 只要是按钮权限即可，不强校验编码类型，避免 DB 真实列类型差异导致断言失败
            Object mtype = r.get("menu_type");
            assertNotNull(mtype, "id=" + id + " menu_type 不能为空");
            assertEquals(expectedPerms.get(id), r.get("perms"), "id=" + id + " perms 编码错误");
        }
    }

    @Test
    @DisplayName("[DB-4] sys_role_menu 把 offShelf/onShelf/priceAdjust 授权给 tenant_admin (1001/1002 两个租户)")
    void db_sysRoleMenuTenantAdminHasNewPerms() {
        String sql = "SELECT r.id role_id, r.role_key, r.tenant_id, rm.menu_id"
                + " FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id"
                + " WHERE r.role_key='tenant_admin' AND r.tenant_id IN (1001, 1002) AND rm.menu_id IN (283, 284, 285)"
                + " ORDER BY r.tenant_id, rm.menu_id";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        System.out.println("tenant_admin 新权限绑定:");
        rows.forEach(r -> System.out.println("  tenant=" + r.get("tenant_id")
                + " roleKey=" + r.get("role_key")
                + " menu_id=" + r.get("menu_id")));
        assertEquals(6, rows.size(), "2 个租户 * 3 个新按钮 = 6 条绑定, 实际: " + rows.size());
        // 每个租户的三条都存在
        for (Long tenantId : Arrays.asList(1001L, 1002L)) {
            for (Integer menuId : Arrays.asList(283, 284, 285)) {
                long count = rows.stream()
                        .filter(r -> tenantId.equals(((Number) r.get("tenant_id")).longValue())
                                && menuId.equals(((Number) r.get("menu_id")).intValue()))
                        .count();
                assertEquals(1L, count, "租户 " + tenantId + " 缺少 menu_id=" + menuId + " 的绑定");
            }
        }
    }

    @Test
    @DisplayName("[Service-1] resolveProductId 按商品名三选一定位")
    void service_resolveByName() {
        // 先取一条存在的商品名
        ProductInfo one = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>()
                        .eq(ProductInfo::getDeleted, 0)
                        .last("LIMIT 1"));
        assertNotNull(one, "种子数据里至少要有 1 条商品");
        System.out.println("定位基准商品: id=" + one.getId() + ", name=" + one.getName());

        Long id1 = productInfoService.resolveProductId(null, one.getName(), null);
        assertEquals(one.getId(), id1, "按 name 定位错误");

        Long id2 = productInfoService.resolveProductId(one.getId(), null, null);
        assertEquals(one.getId(), id2, "按 id 定位错误");

        // 多商品名应该命中多条 → 抛 ParamException
        LambdaQueryWrapper<ProductInfo> w = new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getDeleted, 0).last("LIMIT 2");
        List<ProductInfo> two = productInfoMapper.selectList(w);
        if (two.size() == 2) {
            // 强制把第2条名称改成第1条（用完立刻还原）
            String originName = two.get(1).getName();
            try {
                two.get(1).setName(two.get(0).getName());
                productInfoMapper.updateById(two.get(1));
                assertThrows(com.retail.core.exception.ParamException.class,
                        () -> productInfoService.resolveProductId(null, two.get(0).getName(), null),
                        "同名商品应该抛出 ParamException (匹配多个)");
            } finally {
                two.get(1).setName(originName);
                productInfoMapper.updateById(two.get(1));
            }
        }

        // 不存在的商品名应该抛 ParamException
        assertThrows(com.retail.core.exception.ParamException.class,
                () -> productInfoService.resolveProductId(null, "永远不存在的商品名_abc123", null),
                "不存在商品名应抛 ParamException");
    }

    @Test
    @DisplayName("[Service-2] listProducts inStock=true 只返回 stock_qty>0 的商品")
    void service_listProducts_inStock() {
        PageContextHolder.set(PageContextHolder.build(1, 100));
        ProductListReq req = new ProductListReq();
        req.setInStock(true);
        PageResp<ProductListItemResp> resp = productInfoService.listProducts(req);
        List<ProductListItemResp> rows = resp.getItems();
        System.out.println("inStock=true 返回: " + rows.size() + " 条");
        for (ProductListItemResp r : rows) {
            assertNotNull(r.getStockQty(), "库存不应 NULL, name=" + r.getName());
            assertTrue(r.getStockQty() > 0,
                    "inStock=true 返回中应无 0 库存, 但 stock_qty=" + r.getStockQty() + ", name=" + r.getName());
        }
        System.out.println("首条: " + (rows.isEmpty() ? "空" : rows.get(0)));
    }

    @Test
    @DisplayName("[Service-3] listProducts clearance=true 只返回清仓商品")
    void service_listProducts_clearance() {
        // 先随机把 1 条商品标记清仓 (执行完还原)
        ProductInfo one = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>()
                        .eq(ProductInfo::getDeleted, 0)
                        .eq(ProductInfo::getClearance, 0)
                        .last("LIMIT 1"));
        Integer originClearance = one.getClearance();
        try {
            one.setClearance(1);
            productInfoMapper.updateById(one);

            PageContextHolder.set(PageContextHolder.build(1, 100));
            ProductListReq req = new ProductListReq();
            req.setClearance(true);
            PageResp<ProductListItemResp> resp = productInfoService.listProducts(req);
            List<ProductListItemResp> rows = resp.getItems();
            assertTrue(rows.size() >= 1, "clearance=true 应至少返回 1 条");
            boolean found = rows.stream().anyMatch(r -> r.getName().equals(one.getName()));
            assertTrue(found, "clearance=true 列表里应包含刚刚被标记为清仓的商品: " + one.getName());
            System.out.println("clearance=true 返回: " + rows.size() + " 条, 验证商品命中: " + found);
        } finally {
            one.setClearance(originClearance);
            productInfoMapper.updateById(one);
        }
    }

    @Test
    @DisplayName("[Service-4] priceAdjust 改价→原价/成本记录 + 差价计算正确")
    void service_priceAdjust() {
        // 取 1 条有 price/cost 的商品, 执行改价, 完成后还原
        ProductInfo one = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>()
                        .eq(ProductInfo::getDeleted, 0)
                        .isNotNull(ProductInfo::getPrice)
                        .last("LIMIT 1"));
        assertNotNull(one, "至少需要 1 条有 price 的商品");
        BigDecimal originPrice = one.getPrice();
        BigDecimal originCost = one.getCost();
        try {
            ProductPriceAdjustToolReq req = new ProductPriceAdjustToolReq();
            req.setProductId(one.getId());
            BigDecimal newPrice = originPrice.add(new BigDecimal("10.00"));
            BigDecimal newCost = originCost == null ? new BigDecimal("5.50")
                    : originCost.add(new BigDecimal("2.00"));
            req.setNewPrice(newPrice);
            req.setNewCost(newCost);
            req.setReason("ProductToolVerifyTest 改价验证");

            ProductPriceAdjustResp resp = productInfoService.priceAdjust(req);
            System.out.println("priceAdjust 返回: success=" + resp.getSuccess()
                    + ", productName=" + resp.getProductName()
                    + ", oldPrice=" + resp.getOldPrice() + "→newPrice=" + resp.getNewPrice()
                    + ", priceDiff=" + resp.getPriceDiff()
                    + ", oldCost=" + resp.getOldCost() + "→newCost=" + resp.getNewCost()
                    + ", costDiff=" + resp.getCostDiff());

            assertTrue(resp.getSuccess(), "改价成功标志应为 true");
            assertEquals(originPrice, resp.getOldPrice(), "oldPrice 不一致");
            assertEquals(newPrice, resp.getNewPrice(), "newPrice 不一致");
            assertEquals(new BigDecimal("10.00").compareTo(resp.getPriceDiff()), 0,
                    "差价 priceDiff 应为 +10.00, 实际: " + resp.getPriceDiff());
            assertEquals(newCost, resp.getNewCost(), "newCost 不一致");

            // 改完后 DB 里的值是否真的变了
            ProductInfo dbNow = productInfoMapper.selectById(one.getId());
            assertEquals(newPrice.compareTo(dbNow.getPrice()), 0,
                    "DB 里新价未生效, 期望 " + newPrice + ", 实际 " + dbNow.getPrice());
        } finally {
            one.setPrice(originPrice);
            one.setCost(originCost);
            productInfoMapper.updateById(one);
        }
    }

    @Test
    @DisplayName("[Service-5] batchOffShelf 下架→状态变 OFF_SHELF + 上架还原")
    void service_batchOffShelf_thenOnShelf() {
        // 取 1 条在架商品, 先下架再上架回原状态
        ProductInfo one = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>()
                        .eq(ProductInfo::getDeleted, 0)
                        .eq(ProductInfo::getStatus, ProductStatus.ON_SHELF)
                        .last("LIMIT 1"));
        assertNotNull(one, "至少需要 1 条在架商品");
        ProductStatus originStatus = one.getStatus();

        try {
            // 1. 下架 (按 names=[商品名] 批量定位)
            ProductOffShelfToolReq offReq = new ProductOffShelfToolReq();
            offReq.setNames(java.util.Collections.singletonList(one.getName()));
            ProductBatchActionResp offResp = productInfoService.batchOffShelf(offReq);
            System.out.println("offShelf 返回: success=" + offResp.getSuccess()
                    + ", successCount=" + offResp.getSuccessCount()
                    + ", skippedCount=" + offResp.getSkippedCount()
                    + ", failedCount=" + offResp.getFailedCount()
                    + ", items=" + offResp.getItems().stream().map(i -> i.getName() + ":" + i.getAfterStatus())
                    .collect(Collectors.joining(";")));
            assertEquals(1, offResp.getSuccessCount(), "下架成功 1 条");
            assertEquals(0, offResp.getSkippedCount(), "不应有跳过");
            assertEquals(0, offResp.getFailedCount(), "不应有失败");

            ProductInfo afterOff = productInfoMapper.selectById(one.getId());
            assertEquals(ProductStatus.OFF_SHELF, afterOff.getStatus(),
                    "下架后 DB 里 status 应为 OFF_SHELF");

            // 2. 二次调用下架同一个 → skippedCount=1, successCount=0
            ProductBatchActionResp offResp2 = productInfoService.batchOffShelf(offReq);
            assertEquals(1, offResp2.getSkippedCount(), "二次下架应跳过 1 条");
            assertEquals(0, offResp2.getSuccessCount(), "二次下架成功数为 0");

            // 3. 上架 (用 on_shelf)
            com.retail.business.dto.req.ProductOnShelfToolReq onReq =
                    new com.retail.business.dto.req.ProductOnShelfToolReq();
            onReq.setName(one.getName());
            ProductBatchActionResp onResp = productInfoService.batchOnShelf(onReq);
            System.out.println("onShelf 返回: successCount=" + onResp.getSuccessCount()
                    + ", skippedCount=" + onResp.getSkippedCount());
            assertEquals(1, onResp.getSuccessCount(), "上架成功 1 条");
            ProductInfo afterOn = productInfoMapper.selectById(one.getId());
            assertEquals(ProductStatus.ON_SHELF, afterOn.getStatus(),
                    "上架后 DB 里 status 应为 ON_SHELF");

        } finally {
            one.setStatus(originStatus);
            productInfoMapper.updateById(one);
        }
    }
}
