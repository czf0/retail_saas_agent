package com.retail.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.retail.core.service.BaseServiceImpl;
import com.retail.business.convert.CategoryConvert;
import com.retail.business.dto.req.CategoryCreateReq;
import com.retail.business.dto.req.CategoryUpdateReq;
import com.retail.business.dto.resp.CategoryCreateResp;
import com.retail.business.dto.resp.CategoryDeleteResp;
import com.retail.business.dto.resp.CategoryResp;
import com.retail.business.dto.resp.CategoryTreeNodeResp;
import com.retail.business.entity.ProductCategory;
import com.retail.business.entity.ProductInfo;
import com.retail.business.enums.CategoryStatus;
import com.retail.business.mapper.ProductCategoryMapper;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.service.ProductCategoryService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品分类服务实现.
 * <p>product_category 为逻辑删除表(继承 {@link BaseServiceImpl} 复用逻辑删除审计填充);
 * tenant_id 由多租户拦截器自动注入,代码中不主动赋值.
 * <p>分类支持两级树:一级(parent_id=null)+ 二级(parent_id 指向一级);
 * 删除一级分类时子分类级联迁移到根级(parent_id=null),避免商品 category_id 外键失效.
 * <p>跨模块:ProductInfoMapper 用于 deleteCategory 校验分类下是否存在商品(存在则禁止删除或提示迁移),
 * 遵循铁律 21:Service 引 Mapper 不引 ProductInfoService 防循环依赖.
 */
@Slf4j
@Service
public class ProductCategoryServiceImpl extends BaseServiceImpl<ProductCategoryMapper, ProductCategory> implements ProductCategoryService {

    private final ProductInfoMapper productInfoMapper;

    private final CategoryConvert categoryConvert;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 ProductCategoryMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>productInfoMapper 用于 deleteCategory 校验分类下是否存在商品,避免删除后商品 category_id 指向已删除分类.
     */
    public ProductCategoryServiceImpl(ProductInfoMapper productInfoMapper, CategoryConvert categoryConvert) {
        this.productInfoMapper = productInfoMapper;
        this.categoryConvert = categoryConvert;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryCreateResp createCategory(CategoryCreateReq req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new ParamException("分类名称不能为空");
        }
        String name = req.getName().trim();
        if (req.getParentId() != null) {
            ProductCategory parent = this.getById(req.getParentId());
            if (parent == null) {
                throw new ParamException("父分类不存在");
            }
            if (parent.getParentId() != null) {
                throw new ParamException("分类层级最多2级，不能在子分类下创建子分类");
            }
            Long dup = this.baseMapper.selectCount(
                    new LambdaQueryWrapper<ProductCategory>()
                            .eq(ProductCategory::getParentId, req.getParentId())
                            .eq(ProductCategory::getName, name));
            if (dup != null && dup > 0) {
                throw new ParamException("同级下已存在同名分类");
            }
        } else {
            Long dup = this.baseMapper.selectCount(
                    new LambdaQueryWrapper<ProductCategory>()
                            .isNull(ProductCategory::getParentId)
                            .eq(ProductCategory::getName, name));
            if (dup != null && dup > 0) {
                throw new ParamException("同级下已存在同名分类");
            }
        }
        // 同名字段由 CategoryConvert 自动映射(req→entity);差异字段转化后手动 setter
        ProductCategory entity = categoryConvert.toEntity(req);
        entity.setName(name);                                                      // trim 差异(name 已在上方 trim)
        entity.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());  // 默认值差异
        entity.setStatus(CategoryStatus.ACTIVE);                                   // status 由 Service 赋默认值启用(铁律6:CreateReq 禁 status 字段)
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        // tenant_id 由多租户拦截器自动注入
        this.save(entity);
        log.info("创建商品分类 id={} name={} parentId={} sortOrder={} status={}",
                entity.getId(), entity.getName(), entity.getParentId(),
                entity.getSortOrder(), entity.getStatus());

        CategoryCreateResp resp = new CategoryCreateResp();
        resp.setSuccess(true);
        resp.setMessage("分类创建成功");
        resp.setCategoryId(entity.getId());
        return resp;
    }

    @Override
    public List<CategoryTreeNodeResp> listCategoryTree(boolean activeOnly) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductCategory::getSortOrder).orderByAsc(ProductCategory::getId);
        if (activeOnly) {
            wrapper.eq(ProductCategory::getStatus, CategoryStatus.ACTIVE);
        }
        List<ProductCategory> all = this.list(wrapper);

        // 查询所有商品的 category_id 用于统计(租户由拦截器自动过滤)
        List<ProductInfo> products = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().select(ProductInfo::getCategoryId));
        Map<Long, Integer> countByCategoryId = new HashMap<>();
        for (ProductInfo p : products) {
            if (p.getCategoryId() != null) {
                countByCategoryId.merge(p.getCategoryId(), 1, Integer::sum);
            }
        }

        // 组装节点映射
        Map<Long, CategoryTreeNodeResp> nodeMap = new HashMap<>();
        for (ProductCategory c : all) {
            CategoryTreeNodeResp node = new CategoryTreeNodeResp();
            node.setId(c.getId());
            node.setParentId(c.getParentId());
            node.setName(c.getName());
            node.setSortOrder(c.getSortOrder());
            node.setStatus(c.getStatus() != null ? c.getStatus().getCode() : null);
            node.setChildren(new ArrayList<>());
            node.setProductCount(countByCategoryId.getOrDefault(c.getId(), 0));
            nodeMap.put(c.getId(), node);
        }

        // 内存组装二级树
        List<CategoryTreeNodeResp> roots = new ArrayList<>();
        for (ProductCategory c : all) {
            CategoryTreeNodeResp node = nodeMap.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryTreeNodeResp parent = nodeMap.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                    // 填充父分类名称:从已构建的 nodeMap 取父名,零额外 SQL
                    node.setParentName(parent.getName());
                } else {
                    roots.add(node);
                }
            }
        }

        // 一级分类 product_count = 自身商品数 + 所有子分类商品数总和
        for (CategoryTreeNodeResp root : roots) {
            int sum = root.getProductCount() == null ? 0 : root.getProductCount();
            if (root.getChildren() != null) {
                for (CategoryTreeNodeResp child : root.getChildren()) {
                    sum += child.getProductCount() == null ? 0 : child.getProductCount();
                }
            }
            root.setProductCount(sum);
        }
        return roots;
    }

    @Override
    public CategoryResp getCategory(Long id) {
        ProductCategory c = this.getById(id);
        if (c == null) {
            throw new ParamException("分类不存在");
        }
        // 同名字段由 CategoryConvert 自动映射
        CategoryResp resp = categoryConvert.toResp(c);
        // 填充父分类名称:单次查父实体取 name(this.getById 走本 Service 的 Mapper,非跨模块 Service)
        if (c.getParentId() != null) {
            ProductCategory parent = this.getById(c.getParentId());
            resp.setParentName(parent != null ? parent.getName() : null);
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, CategoryUpdateReq req) {
        ProductCategory c = this.getById(id);
        if (c == null) {
            throw new ParamException("分类不存在");
        }
        boolean nameChanged = false;
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty()) {
                throw new ParamException("分类名称不能为空");
            }
            if (!name.equals(c.getName())) {
                LambdaQueryWrapper<ProductCategory> dup = new LambdaQueryWrapper<ProductCategory>()
                        .ne(ProductCategory::getId, id)
                        .eq(ProductCategory::getName, name);
                if (c.getParentId() == null) {
                    dup.isNull(ProductCategory::getParentId);
                } else {
                    dup.eq(ProductCategory::getParentId, c.getParentId());
                }
                Long cnt = this.baseMapper.selectCount(dup);
                if (cnt != null && cnt > 0) {
                    throw new ParamException("同级下已存在同名分类");
                }
                c.setName(name);
                nameChanged = true;
            }
        }
        if (req.getSortOrder() != null) {
            c.setSortOrder(req.getSortOrder());
        }
        if (req.getStatus() != null) {
            c.setStatus(EnumUtil.fromCode(CategoryStatus.class, req.getStatus()));
        }
        if (req.getDescription() != null) {
            c.setDescription(req.getDescription());
        }
        c.setUpdatedAt(LocalDateTime.now());
        this.updateById(c);

        // 名称变更时,同步更新引用该分类的商品的 category 冗余字符串
        if (nameChanged) {
            String path = buildCategoryPath(c);
            int affected = productInfoMapper.update(null, new LambdaUpdateWrapper<ProductInfo>()
                    .eq(ProductInfo::getCategoryId, id)
                    .set(ProductInfo::getCategory, path));
            log.info("更新商品分类 id={} nameChanged=true newName={} cascadeUpdateProducts={}",
                    id, c.getName(), affected);
        } else {
            log.info("更新商品分类 id={} nameChanged=false sortOrder={} status={}",
                    id, req.getSortOrder(), req.getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryDeleteResp deleteCategory(Long id) {
        ProductCategory c = this.getById(id);
        if (c == null) {
            throw new ParamException("分类不存在");
        }
        int deletedCount = 1;
        int affected = 0;
        if (c.getParentId() == null) {
            // 一级分类:级联软删除所有子分类,并把引用商品的 category_id 置空
            List<ProductCategory> children = this.list(
                    new LambdaQueryWrapper<ProductCategory>().eq(ProductCategory::getParentId, id));
            for (ProductCategory child : children) {
                affected += productInfoMapper.update(null, new LambdaUpdateWrapper<ProductInfo>()
                        .eq(ProductInfo::getCategoryId, child.getId())
                        .set(ProductInfo::getCategoryId, null)
                        .set(ProductInfo::getCategory, ""));
                this.removeById(child.getId());
                deletedCount++;
            }
        }
        // 把引用本分类的商品 category_id 置空
        affected += productInfoMapper.update(null, new LambdaUpdateWrapper<ProductInfo>()
                .eq(ProductInfo::getCategoryId, id)
                .set(ProductInfo::getCategoryId, null)
                .set(ProductInfo::getCategory, ""));
        this.removeById(id);
        log.info("删除商品分类 id={} deletedCount={} affectedProducts={}",
                id, deletedCount, affected);

        CategoryDeleteResp resp = new CategoryDeleteResp();
        resp.setSuccess(true);
        resp.setMessage("分类删除成功");
        resp.setDeletedCount(deletedCount);
        resp.setAffectedProducts(affected);
        return resp;
    }

    private String buildCategoryPath(ProductCategory c) {
        if (c.getParentId() == null) {
            return c.getName();
        }
        ProductCategory parent = this.getById(c.getParentId());
        if (parent == null) {
            return c.getName();
        }
        return parent.getName() + "/" + c.getName();
    }
}
