package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import com.retail.business.dto.req.ProductCreateReq;
import com.retail.business.dto.req.ProductDetailToolReq;
import com.retail.business.dto.req.ProductDeleteToolReq;
import com.retail.business.dto.req.ProductListReq;
import com.retail.business.dto.req.ProductOffShelfToolReq;
import com.retail.business.dto.req.ProductOnShelfToolReq;
import com.retail.business.dto.req.ProductPriceAdjustToolReq;
import com.retail.business.dto.req.ProductQueryToolReq;
import com.retail.business.dto.req.ProductUpdateReq;
import com.retail.business.dto.req.ProductUpdateToolReq;
import com.retail.business.dto.resp.ProductBatchActionResp;
import com.retail.business.dto.resp.ProductCreateResp;
import com.retail.business.dto.resp.ProductDeleteResp;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductPriceAdjustResp;
import com.retail.business.dto.resp.ProductResp;
import com.retail.business.dto.resp.ProductUpdateResp;
import com.retail.business.service.ProductInfoService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;

/**
 * 商品业务 Agent 工具服务 (business="product").
 * <p>
 * 聚合商品域的工具方法,全部围绕「内部人员(店长/运营)自然语言对话」设计:
 * <ul>
 *   <li>{@code product:query}         — 商品列表(多条件筛选:关键词,分类,品牌,价格,低库存,有货,清仓,时间区间 + 分页)</li>
 *   <li>{@code product:detail}        — 商品详情(含 SKU,分类,品牌等)</li>
 *   <li>{@code product:create}        — 创建商品(HITL 审批)</li>
 *   <li>{@code product:update}        — 更新通用字段(HITL 审批;改价/上下架请用下方专用工具,语义更直接)</li>
 *   <li>{@code product:off_shelf}     — 下架(支持按品牌/分类/多个商品名批量圈选,HITL 审批)</li>
 *   <li>{@code product:on_shelf}      — 上架(支持按商品名列表批量,HITL 审批)</li>
 *   <li>{@code product:price_adjust}  — 改价/改成本(HITL 审批,展示原价→新价 + 差价)</li>
 *   <li>{@code product:delete}        — 删除商品(软删,HITL 审批,低频)</li>
 * </ul>
 * <p>
 * 权限说明:ProductInfoController 基于 @SaCheckPermission(business:product:query/add/edit/remove/offShelf/onShelf/priceAdjust)
 * 做 AOP 鉴权,对应 sys_menu F 型按钮 perms(见 seed_reset_all.sql 商品域按钮 id 270-273/283-285);
 * 所有 AgentTool 的 requiredPermission 与 Controller 权限编码一一对应,/allowed 接口会按当前角色过滤掉无权限的工具.
 */
@AgentToolService(business = "product")
public class ProductAgentToolService {

    private final ProductInfoService productInfoService;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public ProductAgentToolService(ProductInfoService productInfoService) {
        this.productInfoService = productInfoService;
    }

    /**
     * 分页查询商品列表 (只读, 支持多条件过滤).
     * <p>
     * 复用 {@link ProductInfoService#listProducts}. 使用 {@link ProductQueryToolReq}(自带分页字段).
     * 定位字段优先使用业务语义(name/brand/category 等),不要求 Agent 知道内部 productId.
     *
     * @param req 查询条件 (keyword / category / categoryId / status / lowStockOnly / inStock / clearance /
     *            brand / price区间 / createTime区间 + 分页)
     * @return 商品列表分页响应
     */
    @AgentTool(
        operation = "query",
        description = "查询商品列表。支持多条件筛选：按商品名/编码关键词、分类（分类ID或分类名）、商品状态、"
                + "品牌、价格区间（最低/最高）、低库存、有货(inStock)、清仓标记(clearance)、创建时间区间。可分页。"
                + "商品状态为整数code：1上架/0下架，必须传数字，如查在售商品传status=1、查已下架传status=0。"
                + "典型触发词："
                + "'有哪些商品'"
                + "'xx品牌的商品'"
                + "'在售商品'"
                + "'100元左右的商品'"
                + "'哪些商品缺货了'"
                + "'有什么有货的饮料'"
                + "'有哪些清仓零食'",
        requiredPermission = "business:product:query",
        outputHint = "返回商品分页列表，每条包含商品名称、分类、品牌、售价、状态（上架/下架）、库存、安全库存、"
                + "是否低于安全库存。展示为 markdown 表格，金额保留 2 位小数。"
    )
    public PageResp<ProductListItemResp> query(ProductQueryToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            ProductListReq listReq = new ProductListReq();
            BeanUtil.copyProperties(req, listReq);
            return productInfoService.listProducts(listReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询商品详情 (只读, 含 SKU 列表,分类信息).
     * <p>
     * 复用 {@link ProductInfoService#getProduct}.
     *
     * @param req 查询条件 (productId / spuCode / name 三选一)
     * @return 商品详情 (含 SKU 列表)
     */
    @AgentTool(
        operation = "detail",
        description = "查询商品详情。支持按商品ID、商品编码或商品名称定位（三选一）。"
                + "返回商品完整信息：名称、价格、成本、品牌、描述、图片URL、SKU列表、分类信息。"
                + "典型触发词："
                + "'商品XX的详细信息'"
                + "'看看XX有哪些规格/颜色/尺码'",
        requiredPermission = "business:product:query",
        outputHint = "返回商品详情结构化文本，SKU 列表用 markdown 表格。"
    )
    public ProductResp detail(ProductDetailToolReq req) {
        Long productId = productInfoService.resolveProductId(req.getProductId(), req.getName(), req.getSpuCode());
        return productInfoService.getProduct(productId);
    }

    /**
     * 创建商品 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductInfoService#createProduct}.
     *
     * @param req 创建请求 (name / categoryId / price / cost / status / description / imageUrl / stockQty / safetyStock + 可选 clearance/shelfLifeDays)
     * @return 创建结果 (含商品 ID)
     */
    @AgentTool(
        operation = "create",
        description = "创建新商品。必填商品名称、分类（分类ID优先，分类名也可）、售价；可选成本、状态、描述、图片URL、初始库存、安全库存。"
                + "状态为整数code：1上架/0下架，必须传数字，如创建在售商品传status=1；不传默认1上架。"
                + "创建后商品状态默认在售。这是破坏性操作，必须等用户确认后才可执行。"
                + "典型触发词："
                + "'帮我上一款新商品：海天生抽500ml，15块，调味品分类'"
                + "'新增商品XX，售价XX'",
        destructive = true,
        requiredPermission = "business:product:add",
        outputHint = "返回创建结果（商品ID、名称、状态），展示为文本提示用户商品已创建成功。"
    )
    public ProductCreateResp create(ProductCreateReq req) {
        return productInfoService.createProduct(req);
    }

    /**
     * 更新商品通用字段 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link ProductInfoService#updateProduct}. 仅用于「通用字段」修改:名称,描述,图片,分类,安全库存,SPU编码,品牌.
     * 注意:改价/改成本用 {@link #priceAdjust},上架/下架用 {@link #offShelf}/{@link #onShelf},
     * 这些专用工具语义更直接,有明确的 HITL 预览,LLM 选工具时应优先选它们.
     *
     * @param req 更新请求 (productId + 可更新字段)
     * @return 更新结果
     */
    @AgentTool(
        operation = "update",
        description = "更新商品通用信息。支持修改商品名称、描述、图片URL、分类、安全库存、SPU编码、品牌等。"
                + "定位需要商品ID，或商品名称/编码（内部会自动转ID）。"
                + "这是破坏性操作，必须等用户确认后才可执行。"
                + "注意：如果是想改价/改成本请用 price_adjust 工具；如果是想上架/下架请用 on_shelf/off_shelf 工具，"
                + "它们的语义更明确并会展示原价→新价/状态预览。"
                + "典型触发词："
                + "'修改商品XX的描述'"
                + "'把XX的品牌改成XX'",
        destructive = true,
        requiredPermission = "business:product:edit",
        outputHint = "返回更新结果（商品ID、更新条数），展示为文本提示用户哪些字段已更新。"
    )
    public ProductUpdateResp update(ProductUpdateToolReq req) {
        Long productId = productInfoService.resolveProductId(req.getProductId(), req.getName(), req.getSpuCode());
        ProductUpdateReq updateReq = new ProductUpdateReq();
        BeanUtil.copyProperties(req, updateReq);
        return productInfoService.updateProduct(productId, updateReq);
    }

    /**
     * 下架商品 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 单商品:productId / name / spuCode 三选一.
     * 批量:brand / categoryId / category / names / productIds(支持多条件 AND 圈选).
     * 单次上限 50 条,HITL 预览「将下架的商品名+售价+库存」清单.
     *
     * @param req 下架入参
     * @return 批量操作结果(success/skipped/failed + 每条明细)
     */
    @AgentTool(
        operation = "off_shelf",
        description = "下架商品。支持两种方式：① 单个商品定位（商品名/编码/ID三选一）；"
                + "② 批量圈选（按品牌名、分类名或分类ID、多个商品名列表、多个ID列表；多条件可组合AND）。"
                + "单次批量上限 50 条，超过请分批。这是破坏性操作，必须等用户确认后才可执行。"
                + "典型触发词："
                + "'把可口可乐下架了'"
                + "'xx品牌的商品全下架'"
                + "'零食分类的商品别卖了'"
                + "'这3款T恤都别卖了（A、B、C）'",
        destructive = true,
        requiredPermission = "business:product:offShelf",
        outputHint = "返回批量下架结果：成功/跳过/失败计数，以及每条商品的名称、原价、库存、状态变更。"
                + "展示为 markdown 表格 + 总结文本，失败的标红原因。"
    )
    public ProductBatchActionResp offShelf(ProductOffShelfToolReq req) {
        return productInfoService.batchOffShelf(req);
    }

    /**
     * 上架商品 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 仅支持「显式定位」:productId / name / spuCode 三选一,或 names / productIds 列表.
     * 不支持按品牌/分类大范围内上架,避免误操作.单次上限 50 条.
     *
     * @param req 上架入参
     * @return 批量操作结果
     */
    @AgentTool(
        operation = "on_shelf",
        description = "上架商品。支持单个定位（商品名/编码/ID三选一），或多个商品名列表/ID列表批量。"
                + "不支持按品牌/分类大范围上架（避免误上架已废弃商品），请显式列出要上架的商品。"
                + "单次批量上限 50 条。这是破坏性操作，必须等用户确认后才可执行。"
                + "典型触发词："
                + "'那款T恤上架吧'"
                + "'促销商品全部开始卖'"
                + "'A、B、C这3款新品上架'",
        destructive = true,
        requiredPermission = "business:product:onShelf",
        outputHint = "返回批量上架结果：成功/跳过/失败计数，以及每条商品的名称、原价、库存、状态变更。"
                + "展示为 markdown 表格 + 总结文本，失败的标红原因。"
    )
    public ProductBatchActionResp onShelf(ProductOnShelfToolReq req) {
        return productInfoService.batchOnShelf(req);
    }

    /**
     * 改价/改成本 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 至少传 newPrice 或 newCost 之一.HITL 展示「原价/成本 → 新价/成本 + 差价」,
     * 用户确认后更新(不写业务日志表,仅应用层日志).
     *
     * @param req 改价入参 (定位三选一 + newPrice/newCost/reason)
     * @return 改价前后对比结果
     */
    @AgentTool(
        operation = "price_adjust",
        description = "调整商品售价或成本。支持按商品名/编码/ID定位（三选一），至少提供新售价(newPrice)或新成本(newCost)之一，可附调价原因。"
                + "会展示原价/成本→新价/成本以及差价，这是破坏性操作，必须等用户确认后才可执行。"
                + "典型触发词："
                + "'XX改成49块'"
                + "'海天生抽改成12块，成本8.5，原因促销'"
                + "'XX加价10块'"
                + "'XX的成本补录成8.5'",
        destructive = true,
        requiredPermission = "business:product:priceAdjust",
        outputHint = "返回改价结果：商品名、原价→新价及差价、原成本→新成本及差价。展示为结构化对比文本，金额保留2位小数。"
    )
    public ProductPriceAdjustResp priceAdjust(ProductPriceAdjustToolReq req) {
        return productInfoService.priceAdjust(req);
    }

    /**
     * 删除商品(软删,HITL 审批,低频).
     * <p>
     * productId / name / spuCode 三选一定位,复用 {@link ProductInfoService#deleteProduct}.
     *
     * @param req 删除入参
     * @return 删除结果
     */
    @AgentTool(
        operation = "delete",
        description = "删除商品（软删除，记录仍在库里但不再出现在列表中）。按商品名/编码/ID三选一定位。"
                + "这是破坏性操作，必须等用户确认后才可执行；如果只是下架，请用 off_shelf 工具（可恢复）。"
                + "典型触发词："
                + "'这款商品录错了删掉'"
                + "'这个废弃的SKU删了'",
        destructive = true,
        requiredPermission = "business:product:remove",
        outputHint = "返回删除结果（商品ID、删除条数），展示为文本提示用户删除成功。"
    )
    public ProductDeleteResp delete(ProductDeleteToolReq req) {
        Long productId = productInfoService.resolveProductId(req.getProductId(), req.getName(), req.getSpuCode());
        return productInfoService.deleteProduct(productId);
    }
}
