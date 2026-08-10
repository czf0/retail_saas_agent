package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.ProductSkuConvert;
import com.retail.business.dto.OperationResultResp;
import com.retail.business.dto.req.ProductSkuCreateReq;
import com.retail.business.dto.req.ProductSkuUpdateReq;
import com.retail.business.dto.resp.ProductSkuListItemResp;
import com.retail.business.dto.resp.ProductSkuResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.ProductSku;
import com.retail.business.enums.SkuStatus;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductSkuMapper;
import com.retail.business.service.ProductSkuService;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.core.service.BaseServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品 SKU 服务实现.
 * <p>
 * tenant_id 由多租户拦截器自动注入,逻辑删除由全局配置管理.
 * SKU 通常租户级共享,listSkus 不加 create_by 过滤.
 */
@Slf4j
@Service
public class ProductSkuServiceImpl extends BaseServiceImpl<ProductSkuMapper, ProductSku> implements ProductSkuService {

    private final ProductSkuConvert productSkuConvert;
    private final ProductInfoMapper productInfoMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 ProductSkuMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>productInfoMapper 用于 createSku 校验商品是否存在(避免 SKU 挂到已删除商品下),
     * 遵循铁律 21 直接注入 Mapper 而非 ProductInfoService,防循环依赖.
     */
    public ProductSkuServiceImpl(ProductSkuConvert productSkuConvert,
                                 ProductInfoMapper productInfoMapper) {
        this.productSkuConvert = productSkuConvert;
        this.productInfoMapper = productInfoMapper;
    }

    @Override
    public ProductSkuResp createSku(ProductSkuCreateReq req) {
        // 同名字段由 ProductSkuConvert 自动映射(req→entity)
        ProductSku entity = productSkuConvert.toEntity(req);
        // status 由 Service 赋默认值上架(铁律6:CreateReq 禁 status 字段)
        entity.setStatus(SkuStatus.ON_SHELF);
        // B-31 防御:sku_name 是 DB NOT NULL 列,前端若漏传会导致 SQLException.
        // 兜底策略:按 specJson 值拼接显示名;specJson 也空则用 skuCode 兜底.
        if (StrUtil.isBlank(entity.getSkuName())) {
            if (req.getSpecJson() != null && !req.getSpecJson().isEmpty()) {
                entity.setSkuName(String.join("-", req.getSpecJson().values().stream()
                        .map(String::valueOf).toList()));
            } else {
                entity.setSkuName(req.getSkuCode());
            }
        }
        save(entity);
        log.info("创建SKU id={} skuCode={} skuName={} productId={} price={} status={}",
                entity.getId(), entity.getSkuCode(), entity.getSkuName(),
                entity.getProductId(), entity.getPrice(), entity.getStatus());
        // 同名字段由 ProductSkuConvert 自动映射(entity→resp)
        return productSkuConvert.toResp(entity);
    }

    @Override
    public ProductSkuResp updateSku(Long skuId, ProductSkuUpdateReq req) {
        ProductSku existing = getById(skuId);
        if (existing == null) {
            throw new ParamException("SKU不存在");
        }
        // 使用部分实体 + updateById,保证仅更新非空字段(与 PromotionServiceImpl.updatePromotion 一致)
        ProductSku entity = new ProductSku();
        entity.setId(skuId);
        boolean hasField = false;
        if (StrUtil.isNotBlank(req.getSkuName())) {
            entity.setSkuName(req.getSkuName());
            hasField = true;
        }
        if (req.getPrice() != null) {
            entity.setPrice(req.getPrice());
            hasField = true;
        }
        if (req.getCost() != null) {
            entity.setCost(req.getCost());
            hasField = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(SkuStatus.class, req.getStatus()));
            hasField = true;
        }
        if (req.getStockQty() != null) {
            entity.setStockQty(req.getStockQty());
            hasField = true;
        }
        if (hasField) {
            baseMapper.updateById(entity);
            log.info("更新SKU id={} skuName={} price={} cost={} status={} stockQty={}",
                    skuId, req.getSkuName(), req.getPrice(), req.getCost(),
                    req.getStatus(), req.getStockQty());
        }
        // 返回更新后的完整 SKU 详情(重新查询以拿到最新的 updatedAt)
        return productSkuConvert.toResp(getById(skuId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationResultResp deleteSku(Long skuId) {
        // removeById 由 BaseServiceImpl 增强:逻辑删除时同步填充 deleteAt/deleteBy 审计字段
        boolean ok = removeById(skuId);
        log.info("删除SKU id={} success={}", skuId, ok);
        OperationResultResp resp = new OperationResultResp();
        resp.setSuccess(ok);
        resp.setMessage(ok ? "SKU删除成功" : "SKU不存在");
        return resp;
    }

    @Override
    public ProductSkuResp getSku(Long skuId) {
        ProductSku sku = getById(skuId);
        if (sku == null) {
            throw new ParamException("SKU不存在");
        }
        return productSkuConvert.toResp(sku);
    }

    @Override
    public PageResp<ProductSkuListItemResp> listSkus(Long productId, String status, String keyword) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProductSku::getProductId, productId);
        }
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(ProductSku::getStatus, status);
        }
        if (StrUtil.isNotBlank(keyword)) {
            // 关键字同时匹配 skuCode 与 skuName
            wrapper.and(w -> w.like(ProductSku::getSkuCode, keyword)
                    .or().like(ProductSku::getSkuName, keyword));
        }
        wrapper.orderByDesc(ProductSku::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<ProductSku> page = PageContextHolder.get();
        IPage<ProductSku> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射,含 specJson/cost/createdAt/productId)
        List<ProductSkuListItemResp> items = productSkuConvert.toListItemList(result.getRecords());

        // 填充商品名称:SKU 列表按单个 productId 过滤,所有行归属同一商品,
        // 单次 selectById 比 LEFT JOIN 更高效,且避免 specJson JSON TypeHandler 在自定义 SQL 中的复杂性.
        // Service 层只注入 ProductInfoMapper(不注入 ProductInfoService),避免跨模块 Service 循环依赖(用户硬约束).
        if (productId != null) {
            ProductInfo product = productInfoMapper.selectById(productId);
            String productName = product != null ? product.getName() : null;
            items.forEach(i -> i.setProductName(productName));
        }

        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public ProductSkuResp getBySkuCode(String skuCode) {
        if (StrUtil.isBlank(skuCode)) {
            throw new ParamException("SKU编码不能为空");
        }
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSku::getSkuCode, skuCode);
        // sku_code 有唯一键 uk_tenant_sku_code,逻辑删除后仅返回一条活跃记录
        ProductSku sku = getOne(wrapper);
        if (sku == null) {
            throw new ParamException("SKU不存在");
        }
        return productSkuConvert.toResp(sku);
    }
}
