package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.StockConvert;
import com.retail.business.convert.StockMovementConvert;
import com.retail.business.dto.req.StockAdjustReq;
import com.retail.business.dto.req.StockCountReq;
import com.retail.business.dto.req.StockMovementQueryReq;
import com.retail.business.dto.req.StockMovementToolReq;
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
import com.retail.business.entity.ProductStock;
import com.retail.business.entity.StockMovement;
import com.retail.business.enums.MovementType;
import com.retail.business.enums.ProductStatus;
import com.retail.business.enums.StockBizType;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductSkuMapper;
import com.retail.business.mapper.ProductStockMapper;
import com.retail.business.mapper.StockMovementMapper;
import com.retail.business.service.StockService;
import com.retail.rbac.entity.SysStore;
import com.retail.rbac.mapper.SysStoreMapper;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.security.LoginUserHolder;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品库存账户与流水服务实现.
 * <p>
 * product_stock 为逻辑删除表(继承 {@link BaseServiceImpl} 复用逻辑删除审计填充);
 * stock_movement 为物理删除表(仅 created_at / create_by).
 * tenant_id / store_id 由多租户 / 门店拦截器自动注入,代码中不主动赋值;
 * adjust / inbound / outbound 标注 {@link Transactional},保证账户更新与流水写入同事务原子性.
 */
@Slf4j
@Service
public class StockServiceImpl extends BaseServiceImpl<ProductStockMapper, ProductStock> implements StockService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StockMovementMapper stockMovementMapper;
    private final StockConvert stockConvert;
    private final StockMovementConvert stockMovementConvert;
    private final ProductInfoMapper productInfoMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SysStoreMapper sysStoreMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 ProductStockMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>productInfoMapper / productSkuMapper / sysStoreMapper 用于 listStocks 与 listMovements
     * 批量回填商品名称,SKU 编码/名称与门店名称,避免前端列表显示 "商品 #xx" / "门店 #xx" 兜底文案.
     * <p>注意:此处直接注入 Mapper 而非 StoreService 等其他 Service,遵循 "Service 层引用 Mapper"
     * 的依赖规范,避免 Service 间循环依赖.
     */
    public StockServiceImpl(StockMovementMapper stockMovementMapper,
                            StockConvert stockConvert,
                            StockMovementConvert stockMovementConvert,
                            ProductInfoMapper productInfoMapper,
                            ProductSkuMapper productSkuMapper,
                            SysStoreMapper sysStoreMapper) {
        this.stockMovementMapper = stockMovementMapper;
        this.stockConvert = stockConvert;
        this.stockMovementConvert = stockMovementConvert;
        this.productInfoMapper = productInfoMapper;
        this.productSkuMapper = productSkuMapper;
        this.sysStoreMapper = sysStoreMapper;
    }

    @Override
    public ProductStock getOrCreateStock(Long productId, Long skuId, Long storeId) {
        if (productId == null) {
            throw new ParamException("商品ID不能为空");
        }
        // store_id 由门店拦截器自动附加过滤,不手动赋值
        ProductStock stock = baseMapper.selectByProductAndSku(productId, skuId);
        if (stock != null) {
            return stock;
        }
        // 不存在则创建(可用/锁定/在途/安全库存默认 0;tenantId / storeId 由拦截器自动注入)
        ProductStock newStock = new ProductStock();
        newStock.setProductId(productId);
        newStock.setSkuId(skuId);
        newStock.setAvailableQty(0);
        newStock.setLockedQty(0);
        newStock.setInTransitQty(0);
        newStock.setSafetyStock(0);
        try {
            save(newStock);
        } catch (DuplicateKeyException e) {
            // 并发创建命中唯一键 uk_tenant_store_sku,重新查询已创建的账户(幂等保证)
            ProductStock existing = baseMapper.selectByProductAndSku(productId, skuId);
            if (existing != null) {
                return existing;
            }
            throw e;
        }
        return newStock;
    }

    @Override
    public ProductStockResp getStock(Long productId, Long skuId, Long storeId) {
        if (productId == null) {
            throw new ParamException("商品ID不能为空");
        }
        ProductStock stock = baseMapper.selectByProductAndSku(productId, skuId);
        if (stock == null) {
            return null;
        }
        ProductStockResp resp = stockConvert.toResp(stock);
        fillBelowSafety(resp);
        return resp;
    }

    @Override
    public ProductStockResp getStockById(Long stockId) {
        ProductStock stock = getById(stockId);
        if (stock == null) {
            return null;
        }
        ProductStockResp resp = stockConvert.toResp(stock);
        fillBelowSafety(resp);
        return resp;
    }

    @Override
    public PageResp<ProductStockResp> listStocks(StockQueryReq req) {
        if (req == null) {
            req = new StockQueryReq();
        }
        LambdaQueryWrapper<ProductStock> wrapper = new LambdaQueryWrapper<>();
        if (req.getProductId() != null) {
            wrapper.eq(ProductStock::getProductId, req.getProductId());
        }
        if (req.getSkuId() != null) {
            wrapper.eq(ProductStock::getSkuId, req.getSkuId());
        }
        // 门店定位:storeName → storeId 反查后显式过滤(门店拦截器仍按当前用户门店兜底隔离,
        // 平台管理员无门店归属时拦截器忽略本表,故此处显式 eq 才能筛到指定门店;门店店员因拦截器锁定自身门店,
        // 传其他门店名会得到空结果,天然防越权)
        Long storeId = resolveStoreId(req.getStoreId(), req.getStoreName());
        if (storeId != null) {
            wrapper.eq(ProductStock::getStoreId, storeId);
        }
        // 低库存:available_qty < safety_stock
        if (Boolean.TRUE.equals(req.getLowStockOnly())) {
            wrapper.apply("available_qty < safety_stock");
        }
        // 在途:in_transit_qty > 0(采购在途)
        if (Boolean.TRUE.equals(req.getInTransitOnly())) {
            wrapper.gt(ProductStock::getInTransitQty, 0);
        }
        // 积压:available_qty > safety_stock(库存压货分析)
        if (Boolean.TRUE.equals(req.getHighStockOnly())) {
            wrapper.apply("available_qty > safety_stock");
        }
        // 业务语义过滤:商品名/品牌/分类/状态不冗余在 product_stock 表,
        // 先反查 product_info 得到符合条件的商品ID集合,再对 product_stock IN 过滤
        List<Long> productIds = resolveProductIdsByBusiness(req);
        if (productIds != null) {
            if (productIds.isEmpty()) {
                // 业务条件无匹配商品,直接返回空页(避免 IN 空集)
                return new PageResp<>(Collections.emptyList(), 0L, 1, 1);
            }
            wrapper.in(ProductStock::getProductId, productIds);
        }
        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<ProductStock> page = PageContextHolder.get();
        IPage<ProductStock> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表;belowSafety 为计算字段,转化后手动 setter
        List<ProductStockResp> items = stockConvert.toRespList(result.getRecords());
        // 批量回填 productName + skuCode (避免 N+1 查询; product_stock 表不冗余商品名称/SKU编码)
        // 解决前端库存列表显示 "商品 #xx" 兜底文案问题
        fillProductAndSkuInfo(items);
        items.forEach(this::fillBelowSafety);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 根据业务语义字段(商品名/品牌/分类/状态)反查商品ID集合.
     * <p>
     * 仅当任一业务字段非空时才执行 product_info 查询;否则返回 null(表示不加 IN 过滤).
     * 返回空集合表示业务条件无匹配商品(调用方据此返回空页).
     *
     * @param req 库存查询请求(含业务过滤字段)
     * @return 匹配的商品ID集合;无业务条件时返回 null
     */
    private List<Long> resolveProductIdsByBusiness(StockQueryReq req) {
        boolean hasBusiness = StrUtil.isNotBlank(req.getProductName())
                || StrUtil.isNotBlank(req.getBrand())
                || req.getCategoryId() != null
                || StrUtil.isNotBlank(req.getCategory())
                || req.getStatus() != null;
        if (!hasBusiness) {
            return null;
        }
        LambdaQueryWrapper<ProductInfo> pw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(req.getProductName())) {
            pw.like(ProductInfo::getName, req.getProductName());
        }
        if (StrUtil.isNotBlank(req.getBrand())) {
            pw.eq(ProductInfo::getBrand, req.getBrand());
        }
        if (req.getCategoryId() != null) {
            pw.eq(ProductInfo::getCategoryId, req.getCategoryId());
        }
        if (StrUtil.isNotBlank(req.getCategory())) {
            pw.like(ProductInfo::getCategory, req.getCategory());
        }
        if (req.getStatus() != null) {
            // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
            pw.eq(ProductInfo::getStatus, EnumUtil.fromCode(ProductStatus.class, req.getStatus()));
        }
        // 仅 SELECT id 列,避免大商品表 SELECT * 拉回全部字段(反查仅需 id 做 IN 过滤)
        pw.select(ProductInfo::getId);
        return productInfoMapper.selectList(pw).stream()
                .map(ProductInfo::getId)
                .collect(Collectors.toList());
    }

    /**
     * 门店定位:storeId 优先,否则按 storeName 反查 sys_store 得门店ID.
     * <p>供「按门店名查询/调拨」场景使用(业务人员说「城西店」,LLM 填 storeName,需反查内部 storeId).
     *
     * @param storeId   显式门店ID(可空)
     * @param storeName 门店名称(可空)
     * @return 门店ID;两者均空返回 null
     * @throws ParamException storeName 非空但门店不存在
     */
    private Long resolveStoreId(Long storeId, String storeName) {
        if (storeId != null) {
            return storeId;
        }
        if (StrUtil.isBlank(storeName)) {
            return null;
        }
        SysStore store = sysStoreMapper.selectOne(
                new LambdaQueryWrapper<SysStore>().eq(SysStore::getStoreName, storeName).last("LIMIT 1"));
        if (store == null) {
            throw new ParamException("未找到门店: " + storeName);
        }
        return store.getId();
    }

    @Override
    public PageResp<StockMovementResp> listMovements(StockMovementQueryReq req) {
        if (req == null) {
            req = new StockMovementQueryReq();
        }
        LambdaQueryWrapper<StockMovement> wrapper = new LambdaQueryWrapper<>();
        if (req.getProductId() != null) {
            wrapper.eq(StockMovement::getProductId, req.getProductId());
        }
        if (req.getMovementType() != null) {
            wrapper.eq(StockMovement::getMovementType, req.getMovementType());
        }
        if (req.getBizType() != null) {
            wrapper.eq(StockMovement::getBizType, req.getBizType());
        }
        if (StrUtil.isNotBlank(req.getBizNo())) {
            wrapper.eq(StockMovement::getBizNo, req.getBizNo());
        }
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());
        if (start != null) {
            wrapper.ge(StockMovement::getCreatedAt, start);
        }
        if (end != null) {
            wrapper.le(StockMovement::getCreatedAt, end);
        }
        wrapper.orderByDesc(StockMovement::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接)
        Page<StockMovement> page = PageContextHolder.get();
        IPage<StockMovement> result = stockMovementMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射)
        List<StockMovementResp> items = stockMovementConvert.toRespList(result.getRecords());
        // 批量回填 productName / skuCode / skuName / storeName (避免 N+1 查询; stock_movement 表不冗余存储这些名称)
        // 解决前端流水列表显示 "商品 #xx" / "门店 #xx" 兜底文案的数据孤岛问题
        fillMovementInfo(items);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustResp adjust(StockAdjustReq req) {
        if (req == null || req.getProductId() == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (req.getChangeQty() == null || req.getChangeQty() == 0) {
            throw new ParamException("调整数量不能为空且不能为0");
        }
        // 1. 获取或创建库存账户
        ProductStock stock = getOrCreateStock(req.getProductId(), req.getSkuId(), req.getStoreId());
        // 2. 校验调整后库存不为负
        int beforeQty = stock.getAvailableQty() == null ? 0 : stock.getAvailableQty();
        int afterQty = beforeQty + req.getChangeQty();
        if (afterQty < 0) {
            throw new ParamException("库存不足，调整后可用库存为 " + afterQty);
        }
        // 3. 更新账户 + 写流水(事务内,before_qty/after_qty 快照)
        // Req 传 Integer code → EnumUtil.fromCode 转枚举(非法 code 抛 ParamException);null 回退 MANUAL
        StockBizType bizType = req.getBizType() != null
                ? EnumUtil.fromCode(StockBizType.class, req.getBizType())
                : StockBizType.MANUAL;
        StockMovement movement = applyChange(stock, req.getProductId(), req.getSkuId(),
                req.getChangeQty(), MovementType.ADJUST, bizType, null, req.getReason());
        log.info("库存调整 productId={} skuId={} changeQty={} before={} after={} bizType={} reason={}",
                req.getProductId(), req.getSkuId(), req.getChangeQty(),
                movement.getBeforeQty(), movement.getAfterQty(), movement.getBizType(), req.getReason());
        // 4. 构建响应
        StockAdjustResp resp = new StockAdjustResp();
        resp.setSuccess(true);
        resp.setMessage("库存调整成功");
        resp.setStockId(stock.getId());
        resp.setAfterQty(movement.getAfterQty());
        resp.setMovementId(movement.getId());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inbound(Long productId, Long skuId, Long storeId, Integer qty, StockBizType bizType, String bizNo, String remark) {
        if (productId == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new ParamException("入库数量必须为正数");
        }
        ProductStock stock = getOrCreateStock(productId, skuId, storeId);
        // 入库:changeQty 为正数
        StockMovement movement = applyChange(stock, productId, skuId, qty, MovementType.INBOUND, bizType, bizNo, remark);
        log.info("库存入库 productId={} skuId={} qty={} before={} after={} bizType={} bizNo={}",
                productId, skuId, qty, movement.getBeforeQty(), movement.getAfterQty(),
                movement.getBizType(), bizNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outbound(Long productId, Long skuId, Long storeId, Integer qty, StockBizType bizType, String bizNo, String remark) {
        if (productId == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (qty == null || qty <= 0) {
            throw new ParamException("出库数量必须为正数");
        }
        ProductStock stock = getOrCreateStock(productId, skuId, storeId);
        // 校验可用库存充足
        int beforeQty = stock.getAvailableQty() == null ? 0 : stock.getAvailableQty();
        if (beforeQty - qty < 0) {
            throw new ParamException("可用库存不足，当前可用 " + beforeQty + "，需出库 " + qty);
        }
        // 出库:changeQty 为负数
        StockMovement movement = applyChange(stock, productId, skuId, -qty, MovementType.OUTBOUND, bizType, bizNo, remark);
        log.info("库存出库 productId={} skuId={} qty={} before={} after={} bizType={} bizNo={}",
                productId, skuId, qty, movement.getBeforeQty(), movement.getAfterQty(),
                movement.getBizType(), bizNo);
    }

    @Override
    public PageResp<StockMovementResp> listStockMovements(StockMovementToolReq req) {
        if (req == null) {
            req = new StockMovementToolReq();
        }
        LambdaQueryWrapper<StockMovement> wrapper = new LambdaQueryWrapper<>();
        // 商品语义定位:productName → productId(未给 productId 时按名称反查,可多命中用 IN 过滤);仅查 id 列
        if (req.getProductId() == null && StrUtil.isNotBlank(req.getProductName())) {
            List<Long> ids = productInfoMapper.selectList(
                            new LambdaQueryWrapper<ProductInfo>()
                                    .select(ProductInfo::getId)
                                    .like(ProductInfo::getName, req.getProductName()))
                    .stream().map(ProductInfo::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new PageResp<>(Collections.emptyList(), 0L, req.getPage(), req.getPageSize());
            }
            wrapper.in(StockMovement::getProductId, ids);
        } else if (req.getProductId() != null) {
            wrapper.eq(StockMovement::getProductId, req.getProductId());
        }
        // 门店语义定位:storeName → storeId
        Long storeId = resolveStoreId(null, req.getStoreName());
        if (storeId != null) {
            wrapper.eq(StockMovement::getStoreId, storeId);
        }
        // 变动/业务类型 Integer code → 枚举(EnumUtil.fromCode 校验非法值);枚举 typeHandler 自动映射为 Integer code
        if (req.getMovementType() != null) {
            wrapper.eq(StockMovement::getMovementType, EnumUtil.fromCode(MovementType.class, req.getMovementType()));
        }
        if (req.getBizType() != null) {
            wrapper.eq(StockMovement::getBizType, EnumUtil.fromCode(StockBizType.class, req.getBizType()));
        }
        if (StrUtil.isNotBlank(req.getBizNo())) {
            wrapper.eq(StockMovement::getBizNo, req.getBizNo());
        }
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());
        if (start != null) {
            wrapper.ge(StockMovement::getCreatedAt, start);
        }
        if (end != null) {
            wrapper.le(StockMovement::getCreatedAt, end);
        }
        wrapper.orderByDesc(StockMovement::getCreatedAt);

        // 分页参数由工具层手动注入 ThreadLocal(HTTP 路径由 PageParameterInterceptor 注入)
        Page<StockMovement> page = PageContextHolder.get();
        IPage<StockMovement> result = stockMovementMapper.selectPage(page, wrapper);
        List<StockMovementResp> items = stockMovementConvert.toRespList(result.getRecords());
        // 批量回填商品/SKU/门店名称(流水表不冗余存储名称)
        fillMovementInfo(items);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountResp count(StockCountReq req) {
        if (req == null || req.getProductId() == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (req.getActualQty() == null || req.getActualQty() < 0) {
            throw new ParamException("实盘数量不能为空且不能为负数");
        }
        ProductStock stock = getOrCreateStock(req.getProductId(), req.getSkuId(), null);
        // 账面数量缺省取当前可用库存
        int bookQty = req.getBookQty() != null
                ? req.getBookQty()
                : (stock.getAvailableQty() == null ? 0 : stock.getAvailableQty());
        int diff = req.getActualQty() - bookQty;

        StockCountResp resp = new StockCountResp();
        resp.setProductName(req.getProductName());
        resp.setBookQty(bookQty);
        resp.setActualQty(req.getActualQty());
        resp.setDiff(diff);

        // 账实相符:不写流水
        if (diff == 0) {
            resp.setSuccess(true);
            resp.setResultType("平账");
            resp.setMessage("账实相符，无需调整");
            resp.setAfterQty(bookQty);
            return resp;
        }
        // 盘盈写 CHECK_GAIN,盘亏写 CHECK_LOSS(同事务)
        MovementType type = diff > 0 ? MovementType.CHECK_GAIN : MovementType.CHECK_LOSS;
        String resultType = diff > 0 ? "盘盈" : "盘亏";
        String bizNo = "COUNT" + System.currentTimeMillis();
        StockMovement movement = applyChange(stock, req.getProductId(), req.getSkuId(), diff, type,
                StockBizType.PURCHASE, bizNo, req.getRemark());
        resp.setSuccess(true);
        resp.setResultType(resultType);
        resp.setMessage(resultType + " " + Math.abs(diff) + " 件");
        resp.setAfterQty(movement.getAfterQty());
        log.info("库存盘点 productId={} skuId={} bookQty={} actualQty={} diff={} resultType={}",
                req.getProductId(), req.getSkuId(), bookQty, req.getActualQty(), diff, resultType);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductStockResp setSafetyStock(StockSafetySetReq req) {
        if (req == null || req.getProductId() == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (req.getSafetyStock() == null || req.getSafetyStock() < 0) {
            throw new ParamException("安全库存不能为空且不能为负数");
        }
        ProductStock stock = getOrCreateStock(req.getProductId(), req.getSkuId(), null);
        // 仅更新 safety_stock(null 字段不参与 UPDATE)
        ProductStock update = new ProductStock();
        update.setId(stock.getId());
        update.setSafetyStock(req.getSafetyStock());
        baseMapper.updateById(update);
        log.info("设置安全库存 productId={} skuId={} safetyStock={}",
                req.getProductId(), req.getSkuId(), req.getSafetyStock());
        // 返回刷新后的账户(含 belowSafety 标记)
        ProductStock refreshed = getById(stock.getId());
        ProductStockResp resp = stockConvert.toResp(refreshed);
        // 回填商品/SKU/门店名称(与 stock:check 一致,避免 Agent 拿不到商品名)
        fillProductAndSkuInfo(Collections.singletonList(resp));
        fillBelowSafety(resp);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTransferResp transfer(StockTransferReq req) {
        if (req == null || req.getProductId() == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (req.getQty() == null || req.getQty() <= 0) {
            throw new ParamException("调拨数量必须为正数");
        }
        Long fromStoreId = resolveStoreId(null, req.getFromStoreName());
        Long toStoreId = resolveStoreId(null, req.getToStoreName());
        if (fromStoreId == null || toStoreId == null) {
            throw new ParamException("源门店/目标门店名称不能为空且必须存在");
        }
        if (fromStoreId.equals(toStoreId)) {
            throw new ParamException("源门店与目标门店不能相同");
        }
        Long tenantId = LoginUserHolder.effectiveTenantId();
        if (tenantId == null) {
            throw new ParamException("缺少租户上下文");
        }
        // 源门店库存(显式指定门店,绕过门店拦截器)
        ProductStock fromStock = baseMapper.selectByStoreSku(tenantId, fromStoreId, req.getProductId(), req.getSkuId());
        int fromBefore = fromStock == null ? 0 : (fromStock.getAvailableQty() == null ? 0 : fromStock.getAvailableQty());
        if (fromBefore < req.getQty()) {
            throw new ParamException("源门店可用库存不足，当前可用 " + fromBefore + "，需调拨 " + req.getQty());
        }
        // 目标门店库存(不存在则显式创建)
        ProductStock toStock = baseMapper.selectByStoreSku(tenantId, toStoreId, req.getProductId(), req.getSkuId());
        if (toStock == null) {
            toStock = new ProductStock();
            toStock.setTenantId(tenantId);
            toStock.setStoreId(toStoreId);
            toStock.setProductId(req.getProductId());
            toStock.setSkuId(req.getSkuId());
            toStock.setAvailableQty(0);
            baseMapper.insertIgnoreTenant(toStock);
        }
        // ================ 原子扣减源门店 / 原子增加目标门店 ================
        // 先扣源(带 available_qty >= qty 条件),失败(影响行数=0)时友好提示"库存不足"并直接抛异常,
        // 后续源扣成功但目标增失败的情况,由外层 @Transactional 整体回滚,保证两门店状态一致.
        int qty = req.getQty();
        int decRows = baseMapper.decreaseAvailable(fromStock.getId(), tenantId, qty);
        if (decRows == 0) {
            Integer cur = baseMapper.selectAvailableById(fromStock.getId());
            if (cur == null) {
                throw new ParamException("源门店库存账户不存在，调拨失败");
            }
            throw new ParamException("源门店可用库存不足，当前可用 " + cur + "，需调拨 " + qty);
        }
        int incRows = baseMapper.increaseAvailable(toStock.getId(), tenantId, qty);
        if (incRows == 0) {
            // 走到这里源扣减已写入事务快照,但未提交;抛异常回滚,源门店自动回到扣减前状态
            throw new ParamException("目标门店库存账户异常，调拨失败，事务回滚");
        }
        // ================ 事务内读新值,反推 BEFORE,保证流水快照绝对准确 ================
        Integer fromAfter = baseMapper.selectAvailableById(fromStock.getId());
        Integer toAfter = baseMapper.selectAvailableById(toStock.getId());
        if (fromAfter == null || toAfter == null) {
            throw new IllegalStateException("调拨后库存状态查询失败 fromStockId=" + fromStock.getId()
                    + " toStockId=" + toStock.getId());
        }
        int fromBeforeFinal = fromAfter + qty;   // 反推 BEFORE: after + 扣减数量
        int toBeforeFinal = toAfter - qty;       // 反推 BEFORE: after - 增加数量
        // 写成对流水:源出库 + 目标入库,同单据号关联(可经 stock:movement 追踪)
        String transferNo = "TRANSFER" + System.currentTimeMillis();
        String createBy = LoginUserHolder.currentUsername();
        writeMovement(tenantId, fromStoreId, req.getProductId(), req.getSkuId(), fromStock.getId(),
                MovementType.OUTBOUND, -qty, fromBeforeFinal, fromAfter,
                StockBizType.ADJUST, transferNo, req.getRemark(), createBy);
        writeMovement(tenantId, toStoreId, req.getProductId(), req.getSkuId(), toStock.getId(),
                MovementType.INBOUND, qty, toBeforeFinal, toAfter,
                StockBizType.ADJUST, transferNo, req.getRemark(), createBy);
        log.info("库存调拨 productId={} skuId={} qty={} fromStore={} toStore={} transferNo={} fromBefore={} fromAfter={} toBefore={} toAfter={}",
                req.getProductId(), req.getSkuId(), qty, fromStoreId, toStoreId, transferNo,
                fromBeforeFinal, fromAfter, toBeforeFinal, toAfter);
        StockTransferResp resp = new StockTransferResp();
        resp.setSuccess(true);
        resp.setMessage("调拨成功");
        resp.setProductName(req.getProductName());
        resp.setFromStoreName(req.getFromStoreName());
        resp.setToStoreName(req.getToStoreName());
        resp.setQty(req.getQty());
        resp.setFromAfterQty(fromAfter);
        resp.setToAfterQty(toAfter);
        resp.setTransferNo(transferNo);
        return resp;
    }

    /**
     * 显式指定门店写入库存流水(绕过门店拦截器,供调拨源/目标门店落流水).
     * <p>同 {@code stock_movement} 的 {@code insertIgnoreTenant},tenant_id / store_id / create_by 显式入参.
     */
    private void writeMovement(Long tenantId, Long storeId, Long productId, Long skuId, Long stockId,
                               MovementType type, int changeQty, int beforeQty, int afterQty,
                               StockBizType bizType, String bizNo, String remark, String createBy) {
        StockMovement movement = new StockMovement();
        movement.setTenantId(tenantId);
        movement.setStoreId(storeId);
        movement.setProductId(productId);
        movement.setSkuId(skuId);
        movement.setStockId(stockId);
        movement.setMovementType(type);
        movement.setChangeQty(changeQty);
        movement.setBeforeQty(beforeQty);
        movement.setAfterQty(afterQty);
        movement.setBizType(bizType);
        movement.setBizNo(bizNo);
        movement.setRemark(remark);
        movement.setCreateBy(createBy);
        stockMovementMapper.insertIgnoreTenant(movement);
    }

    /**
     * 应用库存变动(原子 SQL + 事务内快照,消除"先读后写"丢失更新风险).
     * <p>
     * 改造说明:
     * <ul>
     *   <li>原实现:Java 层取 stock.available_qty → 算 after_qty → updateById 整体覆盖.并发下多个事务
     *       同读旧值 → 分别 UPDATE 新值 → 后到事务覆盖先到事务的变化,丢失一次扣/加("丢失更新").</li>
     *   <li>现实现:{@link ProductStockMapper#increaseAvailable} /
     *       {@link ProductStockMapper#decreaseAvailable} 在 MySQL 行锁下做原子加减
     *       {@code SET available_qty = available_qty ± ? WHERE id=? AND deleted=0 [AND available_qty >= ?]},
     *       并发事务排队串行执行,扣不足时 UPDATE 影响行数 = 0,调用方据此抛异常,绝不超卖.</li>
     *   <li>流水 before/after:原子 UPDATE 后在本事务内 {@link ProductStockMapper#selectAvailableById}
     *       读新值(MVCC 可见自己事务未提交的写),反推 {@code beforeQty = afterQty - changeQty},
     *       保证流水快照与 DB 行状态完全一致,无被并发改值导致快照失真的窗口.</li>
     * </ul>
     *
     * @param stock        库存账户(需携带 id / tenantId)
     * @param productId    商品ID
     * @param skuId        SKU ID
     * @param changeQty    变动数量(正数增加,负数减少,0 则无动作仅写流水)
     * @param movementType 变动类型,取值见 {@link MovementType}
     * @param bizType      业务类型枚举
     * @param bizNo        关联单据号
     * @param remark       备注
     * @return 写入的库存流水
     */
    private StockMovement applyChange(ProductStock stock, Long productId, Long skuId, int changeQty,
                                      MovementType movementType, StockBizType bizType, String bizNo, String remark) {
        Long stockId = stock.getId();
        Long tenantId = stock.getTenantId();
        if (tenantId == null) {
            throw new ParamException("库存账户缺少 tenantId，无法执行原子库存更新");
        }
        int afterQty;
        if (changeQty > 0) {
            int rows = baseMapper.increaseAvailable(stockId, tenantId, changeQty);
            if (rows == 0) {
                throw new ParamException("库存账户不存在或已被删除，无法入库 stockId=" + stockId);
            }
            Integer newVal = baseMapper.selectAvailableById(stockId);
            if (newVal == null) {
                throw new IllegalStateException("原子入库完成但库存账户查询失败，id=" + stockId);
            }
            afterQty = newVal;
        } else if (changeQty < 0) {
            int absQty = Math.abs(changeQty);
            int rows = baseMapper.decreaseAvailable(stockId, tenantId, absQty);
            if (rows == 0) {
                // 区分:账户不存在？还是扣不足？给更友好的错误信息
                Integer cur = baseMapper.selectAvailableById(stockId);
                if (cur == null) {
                    throw new ParamException("库存账户不存在或已被删除，无法扣减库存 stockId=" + stockId);
                }
                throw new ParamException("可用库存不足，当前可用 " + cur + "，需扣减 " + absQty);
            }
            Integer newVal = baseMapper.selectAvailableById(stockId);
            if (newVal == null) {
                throw new IllegalStateException("原子扣减完成但库存账户查询失败，id=" + stockId);
            }
            afterQty = newVal;
        } else {
            // changeQty == 0:语义上无变动,读当前值写入平账流水(调用方通常已在外层拦截跳过,保留仅为鲁棒性)
            Integer cur = baseMapper.selectAvailableById(stockId);
            afterQty = cur == null ? 0 : cur;
        }
        int beforeQty = afterQty - changeQty;   // 反推 BEFORE: after ± changeQty 恰好是原子 UPDATE 前的状态
        StockMovement movement = new StockMovement();
        movement.setProductId(productId);
        movement.setSkuId(skuId);
        movement.setStockId(stockId);
        movement.setMovementType(movementType);
        movement.setChangeQty(changeQty);
        movement.setBeforeQty(beforeQty);
        movement.setAfterQty(afterQty);
        movement.setBizType(bizType);
        movement.setBizNo(bizNo);
        movement.setRemark(remark);
        stockMovementMapper.insert(movement);
        return movement;
    }

    /** 计算 belowSafety 标记:可用库存低于安全库存阈值 */
    private void fillBelowSafety(ProductStockResp resp) {
        resp.setBelowSafety(resp.getAvailableQty() != null && resp.getSafetyStock() != null
                && resp.getAvailableQty() < resp.getSafetyStock());
    }

    /**
     * 批量回填商品名称,SKU 编码与门店名称.
     * <p>product_stock 表不冗余存储 productName / skuCode / storeName, 需从 product_info / product_sku /
     * sys_store 批量查询回填. 采用 selectBatchIds + Map 映射, 避免逐条 N+1 查询; 空集合跳过查询防止全表扫描.
     *
     * @param items 库存响应列表 (原地修改 productName / skuCode / storeName 字段)
     */
    private void fillProductAndSkuInfo(List<ProductStockResp> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // 收集 productId 集合, 批量查询商品名称
        Set<Long> productIds = items.stream()
                .map(ProductStockResp::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!productIds.isEmpty()) {
            // selectBatchIds 受多租户拦截器自动附加 tenant_id, 不会跨租户查到其他租户商品
            Map<Long, String> nameMap = productInfoMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName, (a, b) -> a));
            items.forEach(item -> item.setProductName(nameMap.get(item.getProductId())));
        }
        // 收集 skuId 集合, 批量查询 SKU 编码 (无规格商品 skuId=null, 跳过)
        Set<Long> skuIds = items.stream()
                .map(ProductStockResp::getSkuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!skuIds.isEmpty()) {
            Map<Long, String> skuMap = productSkuMapper.selectBatchIds(skuIds).stream()
                    .collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuCode, (a, b) -> a));
            items.forEach(item -> item.setSkuCode(skuMap.get(item.getSkuId())));
        }
        // 收集 storeId 集合, 批量查询门店名称 (租户中心仓 storeId=null, 跳过)
        Set<Long> storeIds = items.stream()
                .map(ProductStockResp::getStoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!storeIds.isEmpty()) {
            Map<Long, String> storeNameMap = sysStoreMapper.selectBatchIds(storeIds).stream()
                    .collect(Collectors.toMap(SysStore::getId, SysStore::getStoreName, (a, b) -> a));
            items.forEach(item -> item.setStoreName(storeNameMap.get(item.getStoreId())));
        }
    }

    /**
     * 批量回填库存流水的商品名称,SKU 编码,SKU 名称与门店名称.
     * <p>stock_movement 表仅存储 productId / skuId / storeId 外键, 不冗余存储名称, 需从
     * product_info / product_sku / sys_store 批量查询回填. 采用 selectBatchIds + Map 映射,
     * 避免逐条 N+1 查询; 空集合跳过查询防止全表扫描.
     * <p>直接注入 Mapper (productInfoMapper / productSkuMapper / sysStoreMapper) 而非其他 Service,
     * 遵循 "Service 层引用 Mapper" 依赖规范, 避免 Service 间循环依赖.
     *
     * @param items 流水响应列表 (原地修改 productName / skuCode / skuName / storeName 字段)
     */
    private void fillMovementInfo(List<StockMovementResp> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // 批量查询商品名称
        Set<Long> productIds = items.stream()
                .map(StockMovementResp::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productInfoMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName, (a, b) -> a));
        // 批量查询 SKU 编码与名称 (无规格商品 skuId=null, 跳过)
        Set<Long> skuIds = items.stream()
                .map(StockMovementResp::getSkuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProductSku> skuMap = skuIds.isEmpty()
                ? Collections.emptyMap()
                : productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s, (a, b) -> a));
        // 批量查询门店名称
        Set<Long> storeIds = items.stream()
                .map(StockMovementResp::getStoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> storeNameMap = storeIds.isEmpty()
                ? Collections.emptyMap()
                : sysStoreMapper.selectBatchIds(storeIds).stream()
                .collect(Collectors.toMap(SysStore::getId, SysStore::getStoreName, (a, b) -> a));
        // 统一回填
        items.forEach(item -> {
            item.setProductName(nameMap.get(item.getProductId()));
            ProductSku sku = skuMap.get(item.getSkuId());
            if (sku != null) {
                item.setSkuCode(sku.getSkuCode());
                item.setSkuName(sku.getSkuName());
            }
            item.setStoreName(storeNameMap.get(item.getStoreId()));
        });
    }

    /** 起始时间:仅日期时取当天 00:00:00 */
    private LocalDateTime parseStart(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            }
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            throw new ParamException("日期格式错误: " + s);
        }
    }

    /** 结束时间:仅日期时取当天 23:59:59.999999999 */
    private LocalDateTime parseEnd(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(s, DT_FMT);
        } catch (Exception e) {
            throw new ParamException("日期格式错误: " + s);
        }
    }
}
