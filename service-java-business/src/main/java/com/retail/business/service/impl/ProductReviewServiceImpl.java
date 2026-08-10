package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.service.BaseServiceImpl;
import com.retail.business.convert.ReviewConvert;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.ReviewCreateReq;
import com.retail.business.dto.req.ReviewQueryReq;
import com.retail.business.dto.req.ReviewReplyReq;
import com.retail.business.dto.resp.ReviewApproveResp;
import com.retail.business.dto.resp.ReviewCreateResp;
import com.retail.business.dto.resp.ReviewDeleteResp;
import com.retail.business.dto.resp.ReviewListItemResp;
import com.retail.business.dto.resp.ReviewRejectResp;
import com.retail.business.dto.resp.ReviewReplyResp;
import com.retail.business.dto.resp.ReviewResp;
import com.retail.business.dto.resp.ReviewStatsResp;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.ProductReview;
import com.retail.business.enums.ReviewStatus;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductReviewMapper;
import com.retail.business.service.ProductReviewService;
import com.retail.core.enums.EnumUtil;
import com.retail.core.exception.ParamException;
import com.retail.rbac.satoken.DataScopeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品评价服务实现.
 * <p>
 * tenant_id 由多租户拦截器自动注入,逻辑删除由全局配置管理.
 * 商品存在性校验依赖 ProductInfoMapper(由其它模块创建).
 */
@Slf4j
@Service
public class ProductReviewServiceImpl extends BaseServiceImpl<ProductReviewMapper, ProductReview> implements ProductReviewService {

    private final ProductInfoMapper productInfoMapper;

    private final ReviewConvert reviewConvert;

    /** 数据权限辅助:基于角色 data_scope 决定是否附加 create_by 过滤(SELF 角色仅见本人评价) */
    private final DataScopeHelper dataScopeHelper;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 ProductReviewMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>productInfoMapper 用于 createReview 校验商品存在性(未购买商品也可评价,简化场景仅校验商品存在);
     * dataScopeHelper 用于 listReviews 做数据权限裁剪(SELF 角色仅见本人评价).
     */
    public ProductReviewServiceImpl(ProductInfoMapper productInfoMapper, ReviewConvert reviewConvert,
                                    DataScopeHelper dataScopeHelper) {
        this.productInfoMapper = productInfoMapper;
        this.reviewConvert = reviewConvert;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    public ReviewCreateResp createReview(ReviewCreateReq req) {
        if (req.getProductId() == null) {
            throw new ParamException("商品ID不能为空");
        }
        if (req.getRating() == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new ParamException("评分必须在1-5之间");
        }
        // 校验商品存在(tenant_id 由拦截器自动过滤同租户商品)
        Long productCount = productInfoMapper.selectCount(
                new QueryWrapper<ProductInfo>().eq("id", req.getProductId()));
        if (productCount == null || productCount == 0) {
            throw new ParamException("商品不存在");
        }

        // 同名字段由 ReviewConvert 自动映射(req→entity);status 为默认值差异,转化后手动 setter
        ProductReview review = reviewConvert.toEntity(req);
        review.setStatus(ReviewStatus.PENDING);
        save(review);
        log.info("创建商品评价 reviewId={} productId={} rating={} status=pending",
                review.getId(), review.getProductId(), review.getRating());

        ReviewCreateResp resp = new ReviewCreateResp();
        resp.setSuccess(true);
        resp.setMessage("评价创建成功");
        resp.setReviewId(review.getId());
        return resp;
    }

    @Override
    public PageResp<ReviewListItemResp> listReviews(Long productId, Integer rating, Integer status) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProductReview::getProductId, productId);
        }
        if (rating != null) {
            wrapper.eq(ProductReview::getRating, rating);
        }
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (status != null) {
            wrapper.eq(ProductReview::getStatus, EnumUtil.fromCode(ReviewStatus.class, status));
        }
        // 数据权限过滤:角色 data_scope=SELF 的用户仅可见本人创建的评价(超管/ALL 角色不附加条件)
        if (dataScopeHelper.needSelfScope()) {
            wrapper.eq(ProductReview::getCreateBy, dataScopeHelper.currentOperator());
        }
        wrapper.orderByDesc(ProductReview::getCreatedAt);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<ProductReview> page = PageContextHolder.get();
        IPage<ProductReview> result = baseMapper.selectPage(page, wrapper);

        // 转化实体列表为响应列表(同名字段自动映射)
        List<ReviewListItemResp> items = reviewConvert.toListItemList(result.getRecords());

        // 批量查询商品名称:收集非空 productId,一次性 selectBatchIds 查 product_info,构建 ID→名称映射.
        // ProductInfoMapper 已由构造注入(createReview 中用于商品存在性校验),无需额外注入其他 Service.
        Set<Long> productIds = items.stream()
                .map(ReviewListItemResp::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productInfoMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName));
        items.forEach(i -> i.setProductName(productNameMap.get(i.getProductId())));

        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public PageResp<ReviewListItemResp> listReviews(ReviewQueryReq req) {
        if (req == null) {
            req = new ReviewQueryReq();
        }
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        if (req.getProductId() != null) {
            wrapper.eq(ProductReview::getProductId, req.getProductId());
        }
        if (req.getRating() != null) {
            wrapper.eq(ProductReview::getRating, req.getRating());
        }
        // Integer code → 枚举(EnumUtil.fromCode 校验非法值)
        if (req.getStatus() != null) {
            wrapper.eq(ProductReview::getStatus, EnumUtil.fromCode(ReviewStatus.class, req.getStatus()));
        }
        // 商品名称模糊查询:先查 product_info 得商品ID集合,再 IN 过滤(先查ID再过滤)
        if (StrUtil.isNotBlank(req.getProductName())) {
            List<Long> productIds = productInfoMapper.selectList(
                            new QueryWrapper<ProductInfo>().select("id")
                                    .like("name", req.getProductName()))
                    .stream().map(ProductInfo::getId).collect(Collectors.toList());
            if (productIds.isEmpty()) {
                return new PageResp<>(Collections.emptyList(), 0L, 1, 1);
            }
            wrapper.in(ProductReview::getProductId, productIds);
        }
        // 评价内容关键词模糊匹配
        if (StrUtil.isNotBlank(req.getKeyword())) {
            wrapper.like(ProductReview::getContent, req.getKeyword());
        }
        // 评价时间范围过滤(yyyy-MM-dd)
        if (StrUtil.isNotBlank(req.getStartDate())) {
            wrapper.ge(ProductReview::getCreatedAt, LocalDate.parse(req.getStartDate()).atStartOfDay());
        }
        if (StrUtil.isNotBlank(req.getEndDate())) {
            wrapper.le(ProductReview::getCreatedAt, LocalDate.parse(req.getEndDate()).atTime(LocalTime.MAX));
        }
        // 数据权限过滤:角色 data_scope=SELF 的用户仅可见本人创建的评价(超管/ALL 角色不附加条件)
        if (dataScopeHelper.needSelfScope()) {
            wrapper.eq(ProductReview::getCreateBy, dataScopeHelper.currentOperator());
        }
        wrapper.orderByDesc(ProductReview::getCreatedAt);

        Page<ProductReview> page = PageContextHolder.get();
        IPage<ProductReview> result = baseMapper.selectPage(page, wrapper);

        List<ReviewListItemResp> items = reviewConvert.toListItemList(result.getRecords());

        // 批量查询商品名称:收集非空 productId,一次性 selectBatchIds 查 product_info,构建 ID→名称映射.
        Set<Long> productIds = items.stream()
                .map(ReviewListItemResp::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productInfoMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getName));
        items.forEach(i -> i.setProductName(productNameMap.get(i.getProductId())));

        return new PageResp<>(items, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public ReviewResp getReview(Long reviewId) {
        ProductReview r = getById(reviewId);
        if (r == null) {
            throw new ParamException("评价不存在");
        }
        // 同名字段由 ReviewConvert 自动映射
        return reviewConvert.toResp(r);
    }

    @Override
    public ReviewReplyResp replyReview(Long reviewId, ReviewReplyReq req) {
        if (StrUtil.isBlank(req.getReplyContent())) {
            throw new ParamException("回复内容不能为空");
        }
        LambdaUpdateWrapper<ProductReview> uw = new LambdaUpdateWrapper<>();
        uw.eq(ProductReview::getId, reviewId)
                .eq(ProductReview::getDeleted, 0)
                .set(ProductReview::getReplyContent, req.getReplyContent())
                .set(ProductReview::getReplyAt, LocalDateTime.now());
        int rows = baseMapper.update(null, uw);
        log.info("回复评价 reviewId={} rows={}", reviewId, rows);

        ReviewReplyResp resp = new ReviewReplyResp();
        if (rows == 0) {
            resp.setSuccess(false);
            resp.setMessage("评价不存在");
            resp.setReplied(0L);
        } else {
            resp.setSuccess(true);
            resp.setMessage("回复成功");
            resp.setReplied((long) rows);
        }
        return resp;
    }

    @Override
    public ReviewApproveResp approveReview(Long reviewId) {
        LambdaUpdateWrapper<ProductReview> uw = new LambdaUpdateWrapper<>();
        uw.eq(ProductReview::getId, reviewId)
                .eq(ProductReview::getDeleted, 0)
                .set(ProductReview::getStatus, ReviewStatus.APPROVED);
        int rows = baseMapper.update(null, uw);
        log.info("审核通过评价 reviewId={} rows={}", reviewId, rows);

        ReviewApproveResp resp = new ReviewApproveResp();
        if (rows == 0) {
            resp.setSuccess(false);
            resp.setMessage("评价不存在");
            resp.setApproved(0L);
        } else {
            resp.setSuccess(true);
            resp.setMessage("审核通过成功");
            resp.setApproved((long) rows);
        }
        return resp;
    }

    @Override
    public ReviewRejectResp rejectReview(Long reviewId) {
        LambdaUpdateWrapper<ProductReview> uw = new LambdaUpdateWrapper<>();
        uw.eq(ProductReview::getId, reviewId)
                .eq(ProductReview::getDeleted, 0)
                .set(ProductReview::getStatus, ReviewStatus.REJECTED);
        int rows = baseMapper.update(null, uw);
        log.info("审核拒绝评价 reviewId={} rows={}", reviewId, rows);

        ReviewRejectResp resp = new ReviewRejectResp();
        if (rows == 0) {
            resp.setSuccess(false);
            resp.setMessage("评价不存在");
            resp.setRejected(0L);
        } else {
            resp.setSuccess(true);
            resp.setMessage("审核拒绝成功");
            resp.setRejected((long) rows);
        }
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewDeleteResp deleteReview(Long reviewId) {
        boolean ok = removeById(reviewId);
        log.info("删除评价 id={} success={}", reviewId, ok);
        ReviewDeleteResp resp = new ReviewDeleteResp();
        resp.setSuccess(ok);
        resp.setMessage(ok ? "评价删除成功" : "评价不存在");
        resp.setDeleted(ok ? 1L : 0L);
        return resp;
    }

    @Override
    public ReviewStatsResp getReviewStats(Long productId) {
        // 单次查询取出 rating 与 status,在内存中聚合各项统计
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(ProductReview::getProductId, productId);
        }
        wrapper.select(ProductReview::getRating, ProductReview::getStatus);
        List<ProductReview> reviews = baseMapper.selectList(wrapper);

        ReviewStatsResp resp = new ReviewStatsResp();
        int total = reviews.size();
        long sum = 0;
        long positive = 0;
        long approved = 0;
        long pending = 0;
        for (ProductReview r : reviews) {
            if (r.getRating() != null) {
                sum += r.getRating();
                if (r.getRating() >= 4) {
                    positive++;
                }
            }
            if (ReviewStatus.APPROVED.equals(r.getStatus())) {
                approved++;
            } else if (ReviewStatus.PENDING.equals(r.getStatus())) {
                pending++;
            }
        }

        resp.setTotal((long) total);
        if (total > 0) {
            resp.setAvgRating(BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
            // 好评率:rating>=4 占比,按百分比 0-100 保留 2 位小数
            resp.setPositiveRate(BigDecimal.valueOf(positive)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
        } else {
            resp.setAvgRating(BigDecimal.ZERO);
            resp.setPositiveRate(BigDecimal.ZERO);
        }
        resp.setApprovedCount(approved);
        resp.setPendingCount(pending);
        return resp;
    }
}
