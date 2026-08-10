package com.retail.business.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.service.BaseServiceImpl;
import com.retail.business.convert.PromotionConvert;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.core.enums.EnumUtil;
import com.retail.business.dto.req.PromotionCreateReq;
import com.retail.business.dto.req.PromotionQueryReq;
import com.retail.business.dto.req.PromotionUpdateReq;
import com.retail.business.dto.resp.PromotionCreateResp;
import com.retail.business.dto.resp.PromotionDeleteResp;
import com.retail.business.dto.resp.PromotionListItemResp;
import com.retail.business.dto.resp.PromotionResp;
import com.retail.business.dto.resp.PromotionUpdateResp;
import com.retail.business.dto.resp.ProductPromotionItemResp;
import com.retail.business.entity.Promotion;
import com.retail.business.entity.ProductCategory;
import com.retail.business.entity.ProductInfo;
import com.retail.business.enums.PromotionStatus;
import com.retail.business.enums.PromotionType;
import com.retail.business.enums.TargetType;
import com.retail.business.mapper.ProductCategoryMapper;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.PromotionMapper;
import com.retail.business.service.PromotionService;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 促销活动服务实现.
 * <p>
 * tenant_id 由多租户拦截器自动注入,逻辑删除由全局配置管理.
 */
@Slf4j
@Service
public class PromotionServiceImpl extends BaseServiceImpl<PromotionMapper, Promotion> implements PromotionService {

    private final PromotionConvert promotionConvert;
    private final ProductInfoMapper productInfoMapper;
    private final ProductCategoryMapper productCategoryMapper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>productInfoMapper / productCategoryMapper 用于 listPromotions 批量解析 targetIds 为名称,
     * 消除前端列表仅显示 targetType 标签而看不到具体适用对象的数据孤岛.
     * <p>注意:此处直接注入 Mapper 而非 ProductService 等其他 Service,遵循 "Service 层引用 Mapper"
     * 的依赖规范,避免 Service 间循环依赖.
     */
    public PromotionServiceImpl(PromotionConvert promotionConvert,
                                ProductInfoMapper productInfoMapper,
                                ProductCategoryMapper productCategoryMapper) {
        this.promotionConvert = promotionConvert;
        this.productInfoMapper = productInfoMapper;
        this.productCategoryMapper = productCategoryMapper;
    }

    @Override
    public PromotionCreateResp createPromotion(PromotionCreateReq req) {
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new ParamException("开始时间和结束时间不能为空");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new ParamException("开始时间必须早于结束时间");
        }
        // 同名字段由 PromotionConvert 自动映射(req→entity)
        Promotion entity = promotionConvert.toEntity(req);
        // status 为计算字段,转化后手动 setter
        entity.setStatus(PromotionStatus.calculateStatus(req.getStartTime(), req.getEndTime(), LocalDateTime.now()));
        save(entity);
        log.info("创建促销活动 id={} name={} targetType={} startTime={} endTime={} status={}",
                entity.getId(), entity.getName(), entity.getTargetType(),
                entity.getStartTime(), entity.getEndTime(), entity.getStatus());

        PromotionCreateResp resp = new PromotionCreateResp();
        resp.setSuccess(true);
        resp.setMessage("促销活动创建成功");
        resp.setPromotionId(entity.getId());
        return resp;
    }

    @Override
    public PageResp<PromotionListItemResp> listPromotions(Integer status, Integer targetType, String keyword) {
        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<>();
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (status != null) {
            wrapper.eq(Promotion::getStatus, EnumUtil.fromCode(PromotionStatus.class, status));
        }
        if (targetType != null) {
            wrapper.eq(Promotion::getTargetType, EnumUtil.fromCode(TargetType.class, targetType));
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Promotion::getName, keyword);
        }
        wrapper.orderByDesc(Promotion::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<Promotion> page = PageContextHolder.get();
        IPage<Promotion> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射)
        List<PromotionListItemResp> items = promotionConvert.toListItemList(result.getRecords());
        // 批量解析 targetIds 为名称 (product→商品名 / category→分类名 / all→全场商品)
        // 解决前端列表仅显示 targetType 标签而看不到具体适用对象的数据孤岛问题
        fillTargetNames(items);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageResp<PromotionListItemResp> listPromotions(PromotionQueryReq req) {
        if (req == null) {
            req = new PromotionQueryReq();
        }
        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<>();
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (req.getStatus() != null) {
            wrapper.eq(Promotion::getStatus, EnumUtil.fromCode(PromotionStatus.class, req.getStatus()));
        }
        if (req.getTargetType() != null) {
            wrapper.eq(Promotion::getTargetType, EnumUtil.fromCode(TargetType.class, req.getTargetType()));
        }
        if (req.getType() != null) {
            wrapper.eq(Promotion::getType, EnumUtil.fromCode(PromotionType.class, req.getType()));
        }
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.like(Promotion::getName, req.getKeyword());
        }
        // 活动时间范围过滤(yyyy-MM-dd)
        if (StrUtil.isNotBlank(req.getStartDate())) {
            wrapper.ge(Promotion::getStartTime, LocalDate.parse(req.getStartDate()).atStartOfDay());
        }
        if (StrUtil.isNotBlank(req.getEndDate())) {
            wrapper.le(Promotion::getEndTime, LocalDate.parse(req.getEndDate()).atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(Promotion::getCreatedAt);

        Page<Promotion> page = PageContextHolder.get();
        IPage<Promotion> result = baseMapper.selectPage(page, wrapper);

        List<PromotionListItemResp> items = promotionConvert.toListItemList(result.getRecords());
        fillTargetNames(items);
        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /**
     * 批量解析促销活动的适用对象名称.
     * <p>targetIds 为 JSON 数组,存商品ID或分类ID(字符串形式).按 targetType 分类收集 ID 后批量查询:
     * <ul>
     *   <li>product: 查 product_info.name</li>
     *   <li>category: 查 product_category.name</li>
     *   <li>all: 直接填 ["全场商品"]</li>
     * </ul>
     * 采用 selectBatchIds + Map 映射,避免逐活动 N+1 查询;未匹配的 ID 回退为 "ID:xx" 占位.
     * <p>直接注入 Mapper (productInfoMapper / productCategoryMapper) 而非其他 Service,
     * 遵循 "Service 层引用 Mapper" 依赖规范,避免 Service 间循环依赖.
     *
     * @param items 促销列表项 (原地修改 targetNames 字段)
     */
    private void fillTargetNames(List<PromotionListItemResp> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        // 收集 product 类型目标 ID
        Set<Long> productIds = items.stream()
                .filter(i -> TargetType.PRODUCT.equals(EnumUtil.fromCode(TargetType.class, i.getTargetType())))
                .map(PromotionListItemResp::getTargetIds)
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .map(this::parseLongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productInfoMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName, (a, b) -> a));
        // 收集 category 类型目标 ID
        Set<Long> categoryIds = items.stream()
                .filter(i -> TargetType.CATEGORY.equals(EnumUtil.fromCode(TargetType.class, i.getTargetType())))
                .map(PromotionListItemResp::getTargetIds)
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .map(this::parseLongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : productCategoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
        // 按 targetType 统一回填
        items.forEach(i -> {
            TargetType targetType = EnumUtil.fromCode(TargetType.class, i.getTargetType());
            if (TargetType.ALL.equals(targetType)) {
                i.setTargetNames(Collections.singletonList("全场商品"));
            } else if (TargetType.PRODUCT.equals(targetType)) {
                i.setTargetNames(resolveNames(i.getTargetIds(), productNameMap));
            } else if (TargetType.CATEGORY.equals(targetType)) {
                i.setTargetNames(resolveNames(i.getTargetIds(), categoryNameMap));
            }
        });
    }

    /** 安全解析字符串为 Long,失败返回 null(防御 targetIds 存放非数字内容) */
    private Long parseLongId(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 根据 ID 列表与名称映射解析名称,未匹配的 ID 回退为 "ID:xx" 占位 */
    private List<String> resolveNames(List<String> ids, Map<Long, String> nameMap) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(this::parseLongId)
                .filter(Objects::nonNull)
                .map(id -> nameMap.getOrDefault(id, "ID:" + id))
                .collect(Collectors.toList());
    }

    @Override
    public PromotionResp getPromotion(Long promotionId) {
        Promotion p = getById(promotionId);
        if (p == null) {
            throw new ParamException("促销活动不存在");
        }
        // 同名字段由 PromotionConvert 自动映射
        return promotionConvert.toResp(p);
    }

    @Override
    public PromotionUpdateResp updatePromotion(Long promotionId, PromotionUpdateReq req) {
        // 使用部分实体 + updateById,保证 rules 等 JSON 字段经 JacksonTypeHandler 序列化
        Promotion entity = new Promotion();
        entity.setId(promotionId);
        boolean hasField = false;
        if (StrUtil.isNotBlank(req.getName())) {
            entity.setName(req.getName());
            hasField = true;
        }
        if (req.getStatus() != null) {
            entity.setStatus(EnumUtil.fromCode(PromotionStatus.class, req.getStatus()));
            hasField = true;
        }
        if (req.getStartTime() != null) {
            entity.setStartTime(req.getStartTime());
            hasField = true;
        }
        if (req.getEndTime() != null) {
            entity.setEndTime(req.getEndTime());
            hasField = true;
        }
        if (req.getRules() != null) {
            entity.setRules(req.getRules());
            hasField = true;
        }

        PromotionUpdateResp resp = new PromotionUpdateResp();
        if (!hasField) {
            resp.setSuccess(true);
            resp.setMessage("无字段需要更新");
            resp.setUpdated(0L);
            return resp;
        }
        int rows = baseMapper.updateById(entity);
        resp.setSuccess(rows > 0);
        resp.setMessage(rows > 0 ? "促销活动更新成功" : "促销活动不存在");
        resp.setUpdated((long) rows);
        log.info("更新促销活动 id={} rows={} name={} status={} startTime={} endTime={}",
                promotionId, rows, req.getName(), req.getStatus(),
                req.getStartTime(), req.getEndTime());
        return resp;
    }

    @Override
    public PromotionUpdateResp enablePromotion(Long promotionId) {
        PromotionUpdateResp resp = new PromotionUpdateResp();
        Promotion existing = getById(promotionId);
        if (existing == null) {
            resp.setSuccess(false);
            resp.setMessage("促销活动不存在");
            resp.setUpdated(0L);
            return resp;
        }
        Promotion entity = new Promotion();
        entity.setId(promotionId);
        entity.setStatus(PromotionStatus.ACTIVE);
        int rows = baseMapper.updateById(entity);
        resp.setSuccess(rows > 0);
        resp.setMessage(rows > 0 ? "促销活动已启用" : "促销活动启用失败");
        resp.setUpdated((long) rows);
        log.info("启用促销活动 id={} rows={}", promotionId, rows);
        return resp;
    }

    @Override
    public PromotionUpdateResp disablePromotion(Long promotionId) {
        PromotionUpdateResp resp = new PromotionUpdateResp();
        Promotion existing = getById(promotionId);
        if (existing == null) {
            resp.setSuccess(false);
            resp.setMessage("促销活动不存在");
            resp.setUpdated(0L);
            return resp;
        }
        Promotion entity = new Promotion();
        entity.setId(promotionId);
        entity.setStatus(PromotionStatus.PENDING);
        int rows = baseMapper.updateById(entity);
        resp.setSuccess(rows > 0);
        resp.setMessage(rows > 0 ? "促销活动已停用" : "促销活动停用失败");
        resp.setUpdated((long) rows);
        log.info("停用促销活动 id={} rows={}", promotionId, rows);
        return resp;
    }

    @Override
    public PromotionUpdateResp endPromotion(Long promotionId) {
        PromotionUpdateResp resp = new PromotionUpdateResp();
        Promotion existing = getById(promotionId);
        if (existing == null) {
            resp.setSuccess(false);
            resp.setMessage("促销活动不存在");
            resp.setUpdated(0L);
            return resp;
        }
        Promotion entity = new Promotion();
        entity.setId(promotionId);
        entity.setStatus(PromotionStatus.EXPIRED);
        entity.setEndTime(LocalDateTime.now());
        int rows = baseMapper.updateById(entity);
        resp.setSuccess(rows > 0);
        resp.setMessage(rows > 0 ? "促销活动已提前结束" : "促销活动结束失败");
        resp.setUpdated((long) rows);
        log.info("提前结束促销活动 id={} rows={}", promotionId, rows);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionDeleteResp deletePromotion(Long promotionId) {
        boolean ok = removeById(promotionId);
        PromotionDeleteResp resp = new PromotionDeleteResp();
        resp.setSuccess(ok);
        resp.setMessage(ok ? "促销活动删除成功" : "促销活动不存在");
        resp.setDeleted(ok ? 1L : 0L);
        log.info("删除促销活动 id={} success={}", promotionId, ok);
        return resp;
    }

    @Override
    public List<ProductPromotionItemResp> getProductPromotions(Long productId) {
        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Promotion::getStatus, PromotionStatus.ACTIVE);
        List<Promotion> promotions = list(wrapper);

        String pidStr = String.valueOf(productId);
        List<ProductPromotionItemResp> result = new ArrayList<>();
        for (Promotion p : promotions) {
            boolean match = false;
            if (TargetType.ALL.equals(p.getTargetType())) {
                match = true;
            } else if (TargetType.PRODUCT.equals(p.getTargetType())) {
                if (CollUtil.isNotEmpty(p.getTargetIds())) {
                    // target_ids 可能存 int 或 str,统一转 str 比较
                    match = p.getTargetIds().stream()
                            .anyMatch(tid -> String.valueOf(tid).equals(pidStr));
                }
            }
            if (match) {
                // 同名字段由 PromotionConvert 自动映射
                result.add(promotionConvert.toProductPromotionItem(p));
            }
        }
        return result;
    }
}
