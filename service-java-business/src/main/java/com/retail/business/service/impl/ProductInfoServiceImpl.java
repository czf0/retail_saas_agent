package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.service.BaseServiceImpl;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ProductCreateReq;
import com.retail.business.dto.req.ProductListReq;
import com.retail.business.dto.req.ProductOffShelfToolReq;
import com.retail.business.dto.req.ProductOnShelfToolReq;
import com.retail.business.dto.req.ProductPriceAdjustToolReq;
import com.retail.business.dto.req.ProductUpdateReq;
import com.retail.business.dto.resp.ProductBatchActionResp;
import com.retail.business.dto.resp.ProductCreateResp;
import com.retail.business.dto.resp.ProductDeleteResp;
import com.retail.business.dto.resp.ProductListItemResp;
import com.retail.business.dto.resp.ProductPriceAdjustResp;
import com.retail.business.dto.resp.ProductResp;
import com.retail.business.dto.resp.ProductUpdateResp;
import com.retail.business.entity.ProductCategory;
import com.retail.business.entity.ProductInfo;
import com.retail.business.enums.ProductStatus;
import com.retail.business.convert.ProductConvert;
import com.retail.business.mapper.ProductCategoryMapper;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.service.ProductInfoService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.rbac.satoken.DataScopeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品信息服务实现.
 * <p>product_info 为逻辑删除表(继承 {@link BaseServiceImpl} 复用逻辑删除审计填充);
 * tenant_id 由多租户拦截器自动注入,代码中不主动赋值.
 * <p>数据权限(角色 data_scope=SELF)通过 DataScopeHelper 在列表查询层附加 create_by 过滤,仅本人创建的商品可见.
 * <p>跨模块:ProductCategoryMapper 用于按 categoryId 反查父级链拼接 category 路径字符串,
 * 避免 Service 层引用 ProductCategoryService 导致循环依赖(铁律 21:Service 引 Mapper 不引其他 Service).
 */
@Slf4j
@Service
public class ProductInfoServiceImpl extends BaseServiceImpl<ProductInfoMapper, ProductInfo> implements ProductInfoService {

    private final ProductCategoryMapper productCategoryMapper;

    private final ProductConvert productConvert;

    /** 数据权限辅助:基于角色 data_scope 决定是否附加 create_by 过滤(SELF 角色仅见本人数据). */
    private final DataScopeHelper dataScopeHelper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 ProductInfoMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>productCategoryMapper 用于 category 路径拼接(createProduct / updateProduct 时按 categoryId 反查父级链);
     * dataScopeHelper 用于 listProducts 做数据权限裁剪(SELF 角色仅见本人数据).
     */
    public ProductInfoServiceImpl(ProductCategoryMapper productCategoryMapper, ProductConvert productConvert,
                                  DataScopeHelper dataScopeHelper) {
        this.productCategoryMapper = productCategoryMapper;
        this.productConvert = productConvert;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductCreateResp createProduct(ProductCreateReq req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new ParamException("商品名称不能为空");
        }
        if (req.getPrice() == null) {
            throw new ParamException("商品价格不能为空");
        }
        // 同名字段由 ProductConvert 自动映射(req→entity);差异字段转化后手动 setter
        ProductInfo entity = productConvert.toEntity(req);
        entity.setName(req.getName().trim());                                      // trim 差异
        entity.setCost(req.getCost() == null ? BigDecimal.ZERO : req.getCost());  // 默认值差异
        entity.setStatus(ProductStatus.ON_SHELF);                                  // status 由 Service 赋默认值上架(铁律6:CreateReq 禁 status 字段)
        entity.setStockQty(req.getStockQty() == null ? 0 : req.getStockQty());      // 默认值差异
        entity.setSafetyStock(req.getSafetyStock() == null ? 0 : req.getSafetyStock());  // 默认值差异
        // category 为计算字段(依赖 category_id,categoryId 已由 toEntity 映射),转化后手动 setter
        if (req.getCategoryId() != null) {
            entity.setCategory(buildCategoryPath(req.getCategoryId()));
        } else {
            entity.setCategory(req.getCategory() == null ? "" : req.getCategory());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        // tenant_id 由多租户拦截器自动注入
        this.save(entity);
        log.info("创建商品 id={} name={} price={} cost={} status={} categoryId={} stockQty={} safetyStock={}",
                entity.getId(), entity.getName(), entity.getPrice(), entity.getCost(),
                entity.getStatus(), entity.getCategoryId(), entity.getStockQty(), entity.getSafetyStock());

        ProductCreateResp resp = new ProductCreateResp();
        resp.setSuccess(true);
        resp.setMessage("商品创建成功");
        resp.setProductId(entity.getId());
        return resp;
    }

    @Override
    public PageResp<ProductListItemResp> listProducts(ProductListReq req) {
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(req.getCategory())) {
            wrapper.like(ProductInfo::getCategory, req.getCategory());
        }
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (req.getStatus() != null) {
            wrapper.eq(ProductInfo::getStatus, EnumUtil.fromCode(ProductStatus.class, req.getStatus()));
        }
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.like(ProductInfo::getName, req.getKeyword());
        }
        if (req.getCategoryId() != null) {
            // 一级分类时自动包含其所有子分类
            List<Long> idList = new ArrayList<>();
            idList.add(req.getCategoryId());
            List<ProductCategory> children = productCategoryMapper.selectList(
                    new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId, req.getCategoryId()));
            for (ProductCategory ch : children) {
                idList.add(ch.getId());
            }
            wrapper.in(ProductInfo::getCategoryId, idList);
        }
        if (Boolean.TRUE.equals(req.getLowStockOnly())) {
            wrapper.apply("stock_qty < safety_stock");
        }
        // 仅查询有库存的商品(与 lowStockOnly 互补)
        if (Boolean.TRUE.equals(req.getInStock())) {
            wrapper.gt(ProductInfo::getStockQty, 0);
        }
        // 清仓标记过滤(用户问「有哪些清仓商品」)
        if (req.getClearance() != null) {
            wrapper.eq(ProductInfo::getClearance, req.getClearance() ? 1 : 0);
        }
        // 品牌精确过滤
        if (StrUtil.isNotBlank(req.getBrand())) {
            wrapper.eq(ProductInfo::getBrand, req.getBrand());
        }
        // 价格区间过滤(含边界)
        if (req.getMinPrice() != null) {
            wrapper.ge(ProductInfo::getPrice, req.getMinPrice());
        }
        if (req.getMaxPrice() != null) {
            wrapper.le(ProductInfo::getPrice, req.getMaxPrice());
        }
        // 创建时间区间过滤(yyyy-MM-dd → 当天 00:00:00 / 23:59:59)
        if (StrUtil.isNotBlank(req.getCreateTimeStart())) {
            wrapper.ge(ProductInfo::getCreatedAt, LocalDate.parse(req.getCreateTimeStart()).atStartOfDay());
        }
        if (StrUtil.isNotBlank(req.getCreateTimeEnd())) {
            wrapper.le(ProductInfo::getCreatedAt, LocalDate.parse(req.getCreateTimeEnd()).atTime(LocalTime.MAX));
        }
        // 数据权限过滤:角色 data_scope=SELF 的用户仅可见本人创建的商品(超管/ALL 角色不附加条件)
        if (dataScopeHelper.needSelfScope()) {
            wrapper.eq(ProductInfo::getCreateBy, dataScopeHelper.currentOperator());
        }

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        wrapper.orderByDesc(ProductInfo::getId);
        Page<ProductInfo> pageObj = PageContextHolder.get();
        IPage<ProductInfo> result = this.baseMapper.selectPage(pageObj, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射);belowSafety 为计算字段,转化后手动 setter
        List<ProductListItemResp> items = productConvert.toListItemList(result.getRecords());
        items.forEach(i -> i.setBelowSafety(
                i.getStockQty() != null && i.getSafetyStock() != null && i.getStockQty() < i.getSafetyStock()));
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    @Override
    public ProductResp getProduct(Long id) {
        ProductInfo p = this.getById(id);
        if (p == null) {
            throw new ParamException("商品不存在");
        }
        // 同名字段由 ProductConvert 自动映射
        return productConvert.toResp(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductUpdateResp updateProduct(Long id, ProductUpdateReq req) {
        ProductInfo p = this.getById(id);
        if (p == null) {
            throw new ParamException("商品不存在");
        }
        boolean changed = false;
        if (req.getName() != null) {
            p.setName(req.getName().trim());
            changed = true;
        }
        if (req.getPrice() != null) {
            p.setPrice(req.getPrice());
            changed = true;
        }
        if (req.getCost() != null) {
            p.setCost(req.getCost());
            changed = true;
        }
        if (req.getStatus() != null) {
            p.setStatus(EnumUtil.fromCode(ProductStatus.class, req.getStatus()));
            changed = true;
        }
        if (req.getDescription() != null) {
            p.setDescription(req.getDescription());
            changed = true;
        }
        if (req.getImageUrl() != null) {
            p.setImageUrl(req.getImageUrl());
            changed = true;
        }
        if (req.getStockQty() != null) {
            p.setStockQty(req.getStockQty());
            changed = true;
        }
        if (req.getSafetyStock() != null) {
            p.setSafetyStock(req.getSafetyStock());
            changed = true;
        }
        // category_id 变更时同步双写 category 字符串
        if (req.getCategoryId() != null) {
            p.setCategoryId(req.getCategoryId());
            p.setCategory(buildCategoryPath(req.getCategoryId()));
            changed = true;
        } else if (req.getCategory() != null) {
            p.setCategory(req.getCategory());
            changed = true;
        }
        // SPU编码与品牌名(业务模块增强字段,部分更新)
        if (req.getSpuCode() != null) {
            p.setSpuCode(req.getSpuCode());
            changed = true;
        }
        if (req.getBrand() != null) {
            p.setBrand(req.getBrand());
            changed = true;
        }
        if (changed) {
            p.setUpdatedAt(LocalDateTime.now());
            this.updateById(p);
            log.info("更新商品 id={} changed={} name={} price={} status={} stockQty={}",
                    id, changed, req.getName(), req.getPrice(), req.getStatus(), req.getStockQty());
        }
        ProductUpdateResp resp = new ProductUpdateResp();
        resp.setSuccess(true);
        resp.setMessage("商品更新成功");
        resp.setUpdated(1L);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductDeleteResp deleteProduct(Long id) {
        ProductInfo p = this.getById(id);
        if (p == null) {
            throw new ParamException("商品不存在");
        }
        this.removeById(id);
        log.info("删除商品 id={} name={}", id, p.getName());
        ProductDeleteResp resp = new ProductDeleteResp();
        resp.setSuccess(true);
        resp.setMessage("商品删除成功");
        resp.setDeleted(1L);
        return resp;
    }

    private String buildCategoryPath(Long categoryId) {
        ProductCategory c = productCategoryMapper.selectById(categoryId);
        if (c == null) {
            throw new ParamException("商品分类不存在");
        }
        if (c.getParentId() == null) {
            return c.getName();
        }
        ProductCategory parent = productCategoryMapper.selectById(c.getParentId());
        if (parent == null) {
            return c.getName();
        }
        return parent.getName() + "/" + c.getName();
    }

    // ================= 以下为 Agent 工具专用方法(批量操作/改价/定位) =================

    @Override
    public Long resolveProductId(Long productId, String name, String spuCode) {
        if (productId != null) {
            return productId;
        }
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(name)) {
            wrapper.eq(ProductInfo::getName, name.trim());
        } else if (StrUtil.isNotBlank(spuCode)) {
            wrapper.eq(ProductInfo::getSpuCode, spuCode.trim());
        } else {
            throw new ParamException("请提供商品ID、商品名或商品编码之一");
        }
        wrapper.eq(ProductInfo::getDeleted, 0);
        List<ProductInfo> list = this.list(wrapper);
        if (list.isEmpty()) {
            throw new ParamException("未找到匹配的商品（name=" + name + ", spuCode=" + spuCode + "）");
        }
        if (list.size() > 1) {
            throw new ParamException("匹配到多个商品，请用更精确的名称或编码，共 " + list.size() + " 条");
        }
        return list.get(0).getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductBatchActionResp batchOffShelf(ProductOffShelfToolReq req) {
        List<ProductInfo> targets = resolveBatchTargetsOffShelf(req);
        return applyStatusChange(targets, ProductStatus.OFF_SHELF, req.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductBatchActionResp batchOnShelf(ProductOnShelfToolReq req) {
        List<ProductInfo> targets = resolveBatchTargetsOnShelf(req);
        return applyStatusChange(targets, ProductStatus.ON_SHELF, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductPriceAdjustResp priceAdjust(ProductPriceAdjustToolReq req) {
        if (req.getNewPrice() == null && req.getNewCost() == null) {
            throw new ParamException("请至少提供新售价(newPrice)或新成本(newCost)之一");
        }
        Long id = resolveProductId(req.getProductId(), req.getName(), req.getSpuCode());
        ProductInfo p = this.getById(id);
        if (p == null) {
            throw new ParamException("商品不存在 id=" + id);
        }
        BigDecimal oldPrice = p.getPrice();
        BigDecimal oldCost = p.getCost();
        BigDecimal newPrice = req.getNewPrice();
        BigDecimal newCost = req.getNewCost();
        if (newPrice != null) {
            p.setPrice(newPrice);
        }
        if (newCost != null) {
            p.setCost(newCost);
        }
        p.setUpdatedAt(LocalDateTime.now());
        this.updateById(p);
        log.info("商品改价 id={} name={} price({}→{}) cost({}→{}) reason={}",
                id, p.getName(), oldPrice, newPrice, oldCost, newCost, req.getReason());

        ProductPriceAdjustResp resp = new ProductPriceAdjustResp();
        resp.setSuccess(true);
        resp.setMessage("商品调价成功");
        resp.setProductId(id);
        resp.setProductName(p.getName());
        resp.setOldPrice(oldPrice);
        resp.setNewPrice(newPrice != null ? newPrice : oldPrice);
        resp.setPriceDiff(newPrice != null ? newPrice.subtract(oldPrice == null ? BigDecimal.ZERO : oldPrice) : BigDecimal.ZERO);
        resp.setOldCost(oldCost);
        resp.setNewCost(newCost != null ? newCost : oldCost);
        resp.setCostDiff(newCost != null ? newCost.subtract(oldCost == null ? BigDecimal.ZERO : oldCost) : BigDecimal.ZERO);
        return resp;
    }

    // -------- 内部辅助 --------

    /** 下架场景的多维度目标解析(支持 brand/category/categoryId/names 圈选) */
    private List<ProductInfo> resolveBatchTargetsOffShelf(ProductOffShelfToolReq req) {
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductInfo::getDeleted, 0);
        boolean hasCond = false;
        if (req.getProductId() != null) {
            wrapper.eq(ProductInfo::getId, req.getProductId()); hasCond = true;
        } else if (StrUtil.isNotBlank(req.getName())) {
            wrapper.eq(ProductInfo::getName, req.getName().trim()); hasCond = true;
        } else if (StrUtil.isNotBlank(req.getSpuCode())) {
            wrapper.eq(ProductInfo::getSpuCode, req.getSpuCode().trim()); hasCond = true;
        } else if (req.getProductIds() != null && !req.getProductIds().isEmpty()) {
            wrapper.in(ProductInfo::getId, req.getProductIds()); hasCond = true;
        } else if (req.getNames() != null && !req.getNames().isEmpty()) {
            wrapper.in(ProductInfo::getName, req.getNames()); hasCond = true;
        } else {
            // 圈选维度(支持多条件 AND)
            if (StrUtil.isNotBlank(req.getBrand())) {
                wrapper.eq(ProductInfo::getBrand, req.getBrand().trim()); hasCond = true;
            }
            if (req.getCategoryId() != null) {
                List<Long> idList = new ArrayList<>();
                idList.add(req.getCategoryId());
                List<ProductCategory> children = productCategoryMapper.selectList(
                        new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId, req.getCategoryId()));
                for (ProductCategory ch : children) {
                    idList.add(ch.getId());
                }
                wrapper.in(ProductInfo::getCategoryId, idList); hasCond = true;
            } else if (StrUtil.isNotBlank(req.getCategory())) {
                wrapper.like(ProductInfo::getCategory, req.getCategory().trim()); hasCond = true;
            }
        }
        if (!hasCond) {
            throw new ParamException("下架操作必须提供定位条件（商品名/编码/ID/品牌/分类/多个名称），禁止全表下架");
        }
        List<ProductInfo> targets = this.list(wrapper);
        if (targets.isEmpty()) {
            throw new ParamException("根据条件未匹配到任何商品");
        }
        if (targets.size() > 50) {
            throw new ParamException("本次下架匹配到 " + targets.size() + " 条商品，单次批量上限 50 条，请缩小范围或分批执行");
        }
        return targets;
    }

    /** 上架场景的目标解析(只支持显式的单值或列表,避免范围过大误上架) */
    private List<ProductInfo> resolveBatchTargetsOnShelf(ProductOnShelfToolReq req) {
        LambdaQueryWrapper<ProductInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductInfo::getDeleted, 0);
        boolean hasCond = false;
        if (req.getProductId() != null) {
            wrapper.eq(ProductInfo::getId, req.getProductId()); hasCond = true;
        } else if (StrUtil.isNotBlank(req.getName())) {
            wrapper.eq(ProductInfo::getName, req.getName().trim()); hasCond = true;
        } else if (StrUtil.isNotBlank(req.getSpuCode())) {
            wrapper.eq(ProductInfo::getSpuCode, req.getSpuCode().trim()); hasCond = true;
        } else if (req.getProductIds() != null && !req.getProductIds().isEmpty()) {
            wrapper.in(ProductInfo::getId, req.getProductIds()); hasCond = true;
        } else if (req.getNames() != null && !req.getNames().isEmpty()) {
            wrapper.in(ProductInfo::getName, req.getNames()); hasCond = true;
        }
        if (!hasCond) {
            throw new ParamException("上架操作必须提供商品名/编码/ID或显式列表，禁止范围操作");
        }
        List<ProductInfo> targets = this.list(wrapper);
        if (targets.isEmpty()) {
            throw new ParamException("根据条件未匹配到任何商品");
        }
        if (targets.size() > 50) {
            throw new ParamException("本次上架匹配到 " + targets.size() + " 条商品，单次批量上限 50 条，请分批执行");
        }
        return targets;
    }

    /** 统一执行上下架状态变更,返回成功/失败/跳过明细 */
    private ProductBatchActionResp applyStatusChange(List<ProductInfo> targets, ProductStatus target, String reason) {
        String targetLabel = ProductStatus.ON_SHELF.equals(target) ? "上架" : "下架";
        int success = 0, skipped = 0, failed = 0;
        List<ProductBatchActionResp.Item> items = new ArrayList<>();
        for (ProductInfo p : targets) {
            ProductBatchActionResp.Item item = new ProductBatchActionResp.Item();
            item.setProductId(p.getId());
            item.setName(p.getName());
            item.setPrice(p.getPrice());
            item.setStockQty(p.getStockQty());
            item.setBeforeStatus(ProductStatus.ON_SHELF.equals(p.getStatus()) ? "上架" : "下架");
            try {
                if (target.equals(p.getStatus())) {
                    item.setAfterStatus(item.getBeforeStatus());
                    item.setReason("已是" + targetLabel + "状态，跳过");
                    items.add(item);
                    skipped++;
                    continue;
                }
                p.setStatus(target);
                p.setUpdatedAt(LocalDateTime.now());
                this.updateById(p);
                item.setAfterStatus(targetLabel);
                items.add(item);
                success++;
            } catch (Exception ex) {
                item.setAfterStatus(item.getBeforeStatus());
                item.setReason("更新失败: " + ex.getMessage());
                items.add(item);
                failed++;
            }
        }
        log.info("商品{}完成 success={} skipped={} failed={} reason={}",
                targetLabel, success, skipped, failed, reason);
        ProductBatchActionResp resp = new ProductBatchActionResp();
        resp.setSuccess(failed == 0);
        resp.setMessage(failed == 0 ? "商品" + targetLabel + "成功" : "商品" + targetLabel + "部分失败，请查看明细");
        resp.setSuccessCount(success);
        resp.setSkippedCount(skipped);
        resp.setFailedCount(failed);
        resp.setItems(items);
        return resp;
    }
}
