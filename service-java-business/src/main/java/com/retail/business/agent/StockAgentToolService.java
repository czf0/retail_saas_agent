package com.retail.business.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.retail.business.dto.req.StockAdjustReq;
import com.retail.business.dto.req.StockCheckToolReq;
import com.retail.business.dto.req.StockCountReq;
import com.retail.business.dto.req.StockInboundReq;
import com.retail.business.dto.req.StockMovementToolReq;
import com.retail.business.dto.req.StockOutboundReq;
import com.retail.business.dto.req.StockQueryReq;
import com.retail.business.dto.req.StockSafetySetReq;
import com.retail.business.dto.req.StockTransferReq;
import com.retail.business.dto.resp.ProductStockResp;
import com.retail.business.dto.resp.StockAdjustResp;
import com.retail.business.dto.resp.StockCountResp;
import com.retail.business.dto.resp.StockMovementResp;
import com.retail.business.dto.resp.StockTransferResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.ProductSku;
import com.retail.business.enums.StockBizType;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductSkuMapper;
import com.retail.business.service.StockService;
import com.retail.core.annotation.AgentTool;
import com.retail.core.annotation.AgentToolService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;

import java.util.List;

/**
 * 库存业务 Agent 工具服务 (business="stock").
 * <p>
 * 聚合库存域的工具方法, 全部围绕「内部人员(店长/仓库/运营)自然语言对话」设计:
 * <ul>
 *   <li>{@code stock:check}      — 查询库存(按商品名/品牌/分类/门店名/低库存/在途/积压筛选,只读)</li>
 *   <li>{@code stock:movement}   — 查库存流水/出入库明细(只读,支持中文变动类型/业务来源)</li>
 *   <li>{@code stock:inbound}    — 入库(采购到货/退货,HITL 审批)</li>
 *   <li>{@code stock:outbound}   — 出库(领用/报废,HITL 审批)</li>
 *   <li>{@code stock:count}      — 盘点(盘盈/盘亏,HITL 审批)</li>
 *   <li>{@code stock:transfer}   — 门店间调拨(源出库+目标入库同单据号,HITL 审批)</li>
 *   <li>{@code stock:safety_set} — 设置安全库存阈值(HITL 审批)</li>
 *   <li>{@code stock:adjust}     — 手动调整库存余额(HITL 审批)</li>
 * </ul>
 * <p>
 * 权限复用 SaToken:变动类工具对齐 {@code business:stock:adjust},查询类对齐 {@code business:stock:query} /
 * {@code business:stock:movement},与 StockController 各 @SaCheckPermission 编码一一对应.
 * <p>
 * 商品定位统一走 {@link #resolveProduct}(优先业务语义字段 productName/skuCode,其次显式 productId),
 * 业务人员无需知道内部 ID;门店定位由 Service 层经 storeName 反查 sys_store.
 */
@AgentToolService(business = "stock")
public class StockAgentToolService {

    private final StockService stockService;
    private final ProductInfoMapper productInfoMapper;
    private final ProductSkuMapper productSkuMapper;

    /** 单构造器自动注入;显式化依赖,便于测试与可读性 */
    public StockAgentToolService(StockService stockService, ProductInfoMapper productInfoMapper,
                                 ProductSkuMapper productSkuMapper) {
        this.stockService = stockService;
        this.productInfoMapper = productInfoMapper;
        this.productSkuMapper = productSkuMapper;
    }

    /**
     * 商品定位目标(resolveProduct 的返回载体,避免 Java 无 out 参数).
     */
    private static class ProductTarget {
        private Long productId;
        private Long skuId;

        ProductTarget(Long productId, Long skuId) {
            this.productId = productId;
            this.skuId = skuId;
        }
    }

    /**
     * 解析库存操作目标:优先业务语义字段(商品名称/SKU编码),其次显式 productId/skuId.
     * <p>
     * 业务人员通常只掌握商品名称/SKU编码,不掌握内部ID,故提供按业务字段定位的入口.
     * <b>优先语义字段的原因</b>:LLM 在 function calling 时可能生成幻觉的内部 productId
     * (如把不相干数字当 ID),而商品名称/SKU编码来自用户原话更可信;显式 productId 仅在
     * 未提供语义字段,或语义字段多命中且该 ID 命中集合内时采用.
     * 定位无结果时抛出 {@link ParamException} 提示,绝不静默吞错.
     *
     * @param productId   显式商品ID(可空,低优先级)
     * @param productName 商品名称(可空,高优先级)
     * @param skuId       显式SKU ID(可空,低优先级)
     * @param skuCode     SKU 编码(可空,高优先级)
     * @return 解析后的商品定位目标(productId 必非空)
     */
    private ProductTarget resolveProduct(Long productId, String productName, Long skuId, String skuCode) {
        Long resolvedProductId = null;
        Long resolvedSkuId = null;
        // 商品定位:优先按商品名称反查,避免幻觉 productId 带偏;仅查 id 列减少 IO(大商品表避免 SELECT *)
        if (StrUtil.isNotBlank(productName)) {
            List<ProductInfo> products = productInfoMapper.selectList(
                    new LambdaQueryWrapper<ProductInfo>()
                            .select(ProductInfo::getId)
                            .like(ProductInfo::getName, productName));
            if (products.size() == 1) {
                resolvedProductId = products.get(0).getId();
            } else if (products.size() > 1) {
                // 名称多命中:显式 productId 有效且属于命中集合时采用,否则模糊提示
                if (productId != null && products.stream().anyMatch(p -> p.getId().equals(productId))) {
                    resolvedProductId = productId;
                } else {
                    throw new ParamException("商品名称匹配到多个商品，请提供更精确的名称或SKU编码");
                }
            } else if (productId != null) {
                // 名称未命中:回退显式 productId
                resolvedProductId = productId;
            } else {
                throw new ParamException("未找到匹配的商品，请提供更精确的商品名称");
            }
        } else if (productId != null) {
            // 未提供商品名称,仅显式 productId
            resolvedProductId = productId;
        }
        // SKU 定位:优先按 SKU 编码反查,其次显式 skuId;仅查 id 列减少反查 IO
        if (StrUtil.isNotBlank(skuCode)) {
            List<ProductSku> skus = productSkuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>()
                            .select(ProductSku::getId)
                            .like(ProductSku::getSkuCode, skuCode));
            if (skus.size() == 1) {
                resolvedSkuId = skus.get(0).getId();
                // 商品ID未指定时用 SKU 关联商品ID兜底
                if (resolvedProductId == null) {
                    resolvedProductId = skus.get(0).getProductId();
                }
            } else if (skus.size() > 1) {
                if (skuId != null && skus.stream().anyMatch(s -> s.getId().equals(skuId))) {
                    resolvedSkuId = skuId;
                } else {
                    throw new ParamException("SKU编码匹配到多个，请提供更精确的SKU编码");
                }
            } else if (skuId != null) {
                resolvedSkuId = skuId;
            } else {
                throw new ParamException("未找到匹配的SKU，请提供更精确的SKU编码");
            }
        } else if (skuId != null) {
            resolvedSkuId = skuId;
        }
        if (resolvedProductId == null) {
            throw new ParamException("请提供商品ID/商品名称或SKU编码");
        }
        return new ProductTarget(resolvedProductId, resolvedSkuId);
    }

    /**
     * 手动调整库存余额 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 事务内: 取账户 → 更新 available_qty → 写 stock_movement 流水.
     * changeQty 正数增加, 负数减少.
     * <p>
     * 复用 {@link StockService#adjust}, 对齐 StockController.adjust 的 @SaCheckPermission("business:stock:adjust").
     *
     * @param req 调整请求 (productId / skuId / changeQty / reason,可按 productName/skuCode 定位)
     * @return 调整结果 (含账户ID,调整后库存,流水ID)
     */
    @AgentTool(
        operation = "adjust",
        description = "手动调整商品库存余额。changeQty 正数增加库存，负数减少库存。支持按商品ID、商品名称或SKU编码定位商品。此操作会直接修改库存余额，需要用户确认后才可执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回调整结果，包含商品ID、调整数量、调整后库存余额、流水ID。展示为文本。"
    )
    public StockAdjustResp adjust(StockAdjustReq req) {
        // 工具层先按商品名称/SKU编码定位(业务员无需知道内部ID)
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        req.setProductId(target.productId);
        req.setSkuId(target.skuId);
        return stockService.adjust(req);
    }

    /**
     * 查询库存账户列表 (只读, 支持按商品/SKU/门店/低库存/在途/积压筛选).
     * <p>
     * 复用 {@link StockService#listStocks}, 对齐 StockController.list 的 @SaCheckPermission("business:stock:query").
     *
     * @param req 查询条件 (productName / brand / category / status / storeName / lowStockOnly / inTransitOnly / highStockOnly + 分页)
     * @return 库存账户分页列表
     */
    @AgentTool(
        operation = "check",
        description = "查询商品库存。支持按商品名称、品牌、分类、商品状态(上架/下架)、门店名称、低库存(低于安全库存)、"
                + "在途(采购在途中)、积压(远超安全库存)筛选，也可按商品ID精确查询。可分页。"
                + "典型触发词："
                + "'白色T恤还有多少库存'"
                + "'xx品牌库存'"
                + "'化妆品类目的库存'"
                + "'哪些商品缺货了'"
                + "'有哪些采购在途的商品'"
                + "'哪些商品压货了'",
        requiredPermission = "business:stock:query",  // 对齐 StockController.list 的 @SaCheckPermission
        outputHint = "返回库存列表，包含商品名称、SKU、可用库存、锁定库存、在途库存、安全库存、门店、是否低于安全库存。展示为 markdown 表格。"
    )
    public PageResp<ProductStockResp> check(StockCheckToolReq req) {
        // 工具路径不经 HTTP 拦截器,手动注入分页到 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            // StockQueryReq 不承载分页参数(分页由 PageParameterInterceptor 注入 ThreadLocal),业务字段同名复制
            StockQueryReq queryReq = new StockQueryReq();
            BeanUtil.copyProperties(req, queryReq);
            return stockService.listStocks(queryReq);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 查询库存流水/出入库明细 (只读).
     * <p>
     * 复用 {@link StockService#listStockMovements}, 支持变动/业务类型 Integer code 与商品名/门店名定位.
     *
     * @param req 查询条件 (productName / storeName / movementType / bizType / bizNo / 时间区间 + 分页)
     * @return 库存流水分页列表
     */
    @AgentTool(
        operation = "movement",
        description = "查询库存出入库流水明细。支持按商品名称、SKU编码、门店名称、变动类型、业务来源、单据号、时间范围过滤。可分页。"
                + "变动类型为整数code：1入库/2出库/3调整/4锁定/5释放/6盘盈/7盘亏，必须传数字，如查入库流水传movementType=1；"
                + "业务来源为整数code：1订单/2采购/3调整/4退款/5手工，必须传数字，如查采购流水传bizType=2。"
                + "典型触发词："
                + "'海天生抽的库存为什么变了'"
                + "'xx商品最近的出入库记录'"
                + "'采购单xxx的入库流水'",
        requiredPermission = "business:stock:movement",  // 对齐 StockController.movements 的 @SaCheckPermission
        outputHint = "返回流水列表，包含商品名称、变动类型(入库/出库/调整/盘盈/盘亏)、变动数量、变动前后库存、业务来源、单据号、时间。展示为 markdown 表格，按时间倒序。"
    )
    public PageResp<StockMovementResp> movement(StockMovementToolReq req) {
        PageContextHolder.set(PageContextHolder.build(req.getPage(), req.getPageSize()));
        try {
            return stockService.listStockMovements(req);
        } finally {
            PageContextHolder.clear();
        }
    }

    /**
     * 商品入库 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link StockService#inbound}, 面向采购到货/退货回滚场景.
     *
     * @param req 入库请求 (商品定位 + 数量 + 业务来源)
     * @return 入库结果确认文本
     */
    @AgentTool(
        operation = "inbound",
        description = "商品入库，增加库存。按商品名称/SKU编码定位，入库数量必须为正整数，可填采购单号与业务来源。"
                + "业务来源为整数code：1订单/2采购/3调整/4退款/5手工，默认2采购，必须传数字，如采购入库传bizType=2。"
                + "典型触发词："
                + "'这批采购到了，海天生抽入库50件'"
                + "'XX补货入库'"
                + "'退货的商品入库'"
                + "破坏性操作，会直接增加库存余量，需用户确认后才执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回入库确认信息，包含商品名称、入库数量、业务来源。展示为文本。"
    )
    public String inbound(StockInboundReq req) {
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        StockBizType bizType = req.getBizType() != null
                ? EnumUtil.fromCode(StockBizType.class, req.getBizType())
                : StockBizType.PURCHASE;
        stockService.inbound(target.productId, target.skuId, null, req.getQty(), bizType, req.getBizNo(), req.getRemark());
        return "入库成功：" + (StrUtil.isNotBlank(req.getProductName()) ? req.getProductName() : "商品#" + target.productId)
                + " 入库 " + req.getQty() + " 件";
    }

    /**
     * 商品出库 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link StockService#outbound}, 面向领用/报废场景. Service 内部校验可用库存充足.
     *
     * @param req 出库请求 (商品定位 + 数量 + 业务来源)
     * @return 出库结果确认文本
     */
    @AgentTool(
        operation = "outbound",
        description = "商品出库，减少库存。按商品名称/SKU编码定位，出库数量必须为正整数且不超过现有可用库存，可填业务来源。"
                + "业务来源为整数code：1订单/2采购/3调整/4退款/5手工，默认5手工，必须传数字，如手工出库传bizType=5。"
                + "典型触发词："
                + "'领用10件矿泉水做活动'"
                + "'XX报废出库'"
                + "破坏性操作，会直接扣减库存余量，需用户确认后才执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回出库确认信息，包含商品名称、出库数量、业务来源。展示为文本。"
    )
    public String outbound(StockOutboundReq req) {
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        StockBizType bizType = req.getBizType() != null
                ? EnumUtil.fromCode(StockBizType.class, req.getBizType())
                : StockBizType.MANUAL;
        stockService.outbound(target.productId, target.skuId, null, req.getQty(), bizType, req.getBizNo(), req.getRemark());
        return "出库成功：" + (StrUtil.isNotBlank(req.getProductName()) ? req.getProductName() : "商品#" + target.productId)
                + " 出库 " + req.getQty() + " 件";
    }

    /**
     * 商品盘点 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link StockService#count}, 录入实盘数量自动计算盘盈/盘亏.
     *
     * @param req 盘点请求 (商品定位 + 实盘数量)
     * @return 盘点结果 (账面/实盘/盘差/结果类型/调整后库存)
     */
    @AgentTool(
        operation = "count",
        description = "商品盘点，录入实盘数量，系统自动对比账面库存计算盘盈盘亏并调整库存。按商品名称/SKU编码定位，填实盘数量。"
                + "典型触发词："
                + "'盘点海天生抽，账面10实盘8，盘亏2'"
                + "'XX盘亏了'"
                + "'XX实际比账面多了3件，盘盈'"
                + "破坏性操作，会按盘差调整库存余量，需用户确认后才执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回盘点结果，包含商品名称、账面数量、实盘数量、盘差、结果类型(盘盈/盘亏/平账)、调整后库存。展示为文本。"
    )
    public StockCountResp count(StockCountReq req) {
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        req.setProductId(target.productId);
        req.setSkuId(target.skuId);
        return stockService.count(req);
    }

    /**
     * 门店间调拨 (破坏性操作, 触发 HITL 审批).
     * <p>
     * 复用 {@link StockService#transfer}, 源门店出库 + 目标门店入库,两条流水同单据号关联成对.
     *
     * @param req 调拨请求 (商品定位 + 源/目标门店名 + 数量)
     * @return 调拨结果 (源/目标门店调拨后库存 + 调拨单号)
     */
    @AgentTool(
        operation = "transfer",
        description = "门店间调拨商品库存。按商品名称/SKU编码定位，指定调拨数量、源门店名称和目标门店名称，"
                + "系统从源门店出库并给目标门店入库（同一单据号关联两笔流水）。"
                + "典型触发词："
                + "'从城西店调10件海天生抽到滨江店'"
                + "'把XX从城北店调5件到萧山店'"
                + "破坏性操作，会同时改变两个门店库存，需用户确认后才执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回调拨结果，包含商品名称、源门店、目标门店、调拨数量、源门店调拨后持仓、目标门店调拨后持仓、调拨单号。展示为文本。"
    )
    public StockTransferResp transfer(StockTransferReq req) {
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        req.setProductId(target.productId);
        req.setSkuId(target.skuId);
        return stockService.transfer(req);
    }

    /**
     * 设置安全库存阈值 (破坏性操作, 触发 HITL 审批).
     *
     * @param req 请求 (商品定位 + 新安全库存)
     * @return 更新后的库存账户 (含 belowSafety 标记)
     */
    @AgentTool(
        operation = "safety_set",
        description = "设置商品安全库存阈值，用于缺货预警判断。按商品名称/SKU编码定位，填新的安全库存数值(非负整数)。"
                + "典型触发词："
                + "'把海天生抽的安全库存设为50'"
                + "'XX安全库存调成100'"
                + "破坏性操作，会改变缺货预警标准，需用户确认后才执行。",
        requiredPermission = "business:stock:adjust",
        destructive = true,
        outputHint = "返回更新后的库存账户，包含商品名称、新的安全库存、当前可用库存、是否低于安全库存。展示为文本。"
    )
    public ProductStockResp safety_set(StockSafetySetReq req) {
        ProductTarget target = resolveProduct(req.getProductId(), req.getProductName(), req.getSkuId(), req.getSkuCode());
        req.setProductId(target.productId);
        req.setSkuId(target.skuId);
        return stockService.setSafetyStock(req);
    }
}
