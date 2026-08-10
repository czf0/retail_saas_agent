package com.retail.business.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.business.convert.StatsConvert;
import com.retail.business.dto.req.StatsOverviewReq;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.resp.InventoryRecordResp;
import com.retail.business.dto.resp.MemberResp;
import com.retail.business.dto.resp.OrderTrendResp;
import com.retail.business.dto.resp.SalesRecordResp;
import com.retail.business.dto.resp.StatsOverviewResp;
import com.retail.business.entity.InventoryRecord;
import com.retail.business.entity.Member;
import com.retail.business.entity.OrderTrendRecord;
import com.retail.business.entity.ProductInfo;
import com.retail.business.entity.ProductReview;
import com.retail.business.entity.Promotion;
import com.retail.business.entity.SalesRecord;
import com.retail.business.mapper.InventoryRecordMapper;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.mapper.OrderTrendMapper;
import com.retail.business.mapper.ProductInfoMapper;
import com.retail.business.mapper.ProductReviewMapper;
import com.retail.business.mapper.PromotionMapper;
import com.retail.business.mapper.SalesRecordMapper;
import com.retail.business.service.StatsService;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 统计概览服务实现.
 * <p>
 * 所有统计表均为多租户表,tenant_id 由拦截器自动注入过滤;
 * 日期参数支持 "yyyy-MM-dd" 或 "yyyy-MM-dd HH:mm:ss" 两种格式.
 */
@Slf4j
@Service
public class StatsServiceImpl implements StatsService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SalesRecordMapper salesRecordMapper;
    private final InventoryRecordMapper inventoryRecordMapper;
    private final OrderTrendMapper orderTrendMapper;
    private final MemberMapper memberMapper;
    private final ProductInfoMapper productInfoMapper;
    private final PromotionMapper promotionMapper;
    private final ProductReviewMapper productReviewMapper;
    private final StatsConvert statsConvert;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>注意:本类不继承 {@link BaseServiceImpl}(统计模块多表聚合,无主实体 Mapper),
     * 所有 Mapper 显式声明注入:salesRecord / inventoryRecord / orderTrend 三张按日预聚合快照表,
     * memberMapper / productInfoMapper / promotionMapper / productReviewMapper 用于 overview 接口实时汇总核心指标;
     * statsConvert 统一做实体 → 响应 DTO 转换(枚举字段自动映射).
     */
    public StatsServiceImpl(SalesRecordMapper salesRecordMapper,
                            InventoryRecordMapper inventoryRecordMapper,
                            OrderTrendMapper orderTrendMapper,
                            MemberMapper memberMapper,
                            ProductInfoMapper productInfoMapper,
                            PromotionMapper promotionMapper,
                            ProductReviewMapper productReviewMapper,
                            StatsConvert statsConvert) {
        this.salesRecordMapper = salesRecordMapper;
        this.inventoryRecordMapper = inventoryRecordMapper;
        this.orderTrendMapper = orderTrendMapper;
        this.memberMapper = memberMapper;
        this.productInfoMapper = productInfoMapper;
        this.promotionMapper = promotionMapper;
        this.productReviewMapper = productReviewMapper;
        this.statsConvert = statsConvert;
    }

    @Override
    public List<SalesRecordResp> querySales(String startDate, String endDate) {
        log.debug("查询销售统计 startDate={} endDate={}", startDate, endDate);
        LambdaQueryWrapper<SalesRecord> wrapper = new LambdaQueryWrapper<>();
        LocalDateTime start = parseStart(startDate);
        LocalDateTime end = parseEnd(endDate);
        if (start != null) {
            wrapper.ge(SalesRecord::getRecordDate, start);
        }
        if (end != null) {
            wrapper.le(SalesRecord::getRecordDate, end);
        }
        wrapper.orderByDesc(SalesRecord::getRecordDate);
        List<SalesRecord> list = salesRecordMapper.selectList(wrapper);
        log.debug("查询销售统计完成 命中数={}", list.size());
        // 同名字段由 StatsConvert 自动映射
        return statsConvert.toRespList(list);
    }

    @Override
    public List<InventoryRecordResp> queryInventory(Boolean lowStockOnly) {
        log.debug("查询库存统计 lowStockOnly={}", lowStockOnly);
        LambdaQueryWrapper<InventoryRecord> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(lowStockOnly)) {
            wrapper.apply("stock_qty < safety_stock");
        }
        wrapper.orderByDesc(InventoryRecord::getId);
        List<InventoryRecord> list = inventoryRecordMapper.selectList(wrapper);
        // 转化实体列表为响应列表;belowSafety 为计算字段,转化后手动 setter
        List<InventoryRecordResp> items = statsConvert.toInventoryRespList(list);
        items.forEach(r -> r.setBelowSafety(
                r.getStockQty() != null && r.getSafetyStock() != null && r.getStockQty() < r.getSafetyStock()));
        log.debug("查询库存统计完成 命中数={} belowSafetyCount={}",
                items.size(), items.stream().filter(InventoryRecordResp::getBelowSafety).count());
        return items;
    }

    @Override
    public List<OrderTrendResp> queryOrderTrend(String startDate, String endDate) {
        log.debug("查询订单趋势 startDate={} endDate={}", startDate, endDate);
        LambdaQueryWrapper<OrderTrendRecord> wrapper = new LambdaQueryWrapper<>();
        LocalDateTime start = parseStart(startDate);
        LocalDateTime end = parseEnd(endDate);
        if (start != null) {
            wrapper.ge(OrderTrendRecord::getStatDate, start);
        }
        if (end != null) {
            wrapper.le(OrderTrendRecord::getStatDate, end);
        }
        wrapper.orderByDesc(OrderTrendRecord::getStatDate);
        List<OrderTrendRecord> list = orderTrendMapper.selectList(wrapper);
        log.debug("查询订单趋势完成 命中数={}", list.size());
        // 同名字段由 StatsConvert 自动映射
        return statsConvert.toOrderTrendRespList(list);
    }

    @Override
    public PageResp<MemberResp> queryMembers(String level) {
        log.debug("查询会员统计 level={}", level);

        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(level)) {
            wrapper.eq(Member::getLevel, level);
        }
        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        wrapper.orderByDesc(Member::getId);
        Page<Member> pageObj = PageContextHolder.get();
        IPage<Member> result = memberMapper.selectPage(pageObj, wrapper);

        // 同名字段由 StatsConvert 自动映射
        List<MemberResp> items = statsConvert.toMemberRespList(result.getRecords());
        log.debug("查询会员统计完成 total={} 当前页命中={}", result.getTotal(), items.size());
        return new PageResp<>(items, result.getTotal(), (int) pageObj.getCurrent(), (int) pageObj.getSize());
    }

    @Override
    public StatsOverviewResp overview() {
        StatsOverviewResp resp = new StatsOverviewResp();
        resp.setProductCount(productInfoMapper.selectCount(null));
        resp.setPromotionCount(promotionMapper.selectCount(null));
        resp.setReviewCount(productReviewMapper.selectCount(null));
        resp.setMemberCount(memberMapper.selectCount(null));
        log.debug("查询统计概览 productCount={} promotionCount={} reviewCount={} memberCount={}",
                resp.getProductCount(), resp.getPromotionCount(), resp.getReviewCount(), resp.getMemberCount());
        return resp;
    }

    @Override
    public StatsOverviewResp overview(StatsOverviewReq req) {
        if (req == null) {
            return overview();
        }
        LocalDateTime start = parseStart(req.getStartDate());
        LocalDateTime end = parseEnd(req.getEndDate());

        // 商品数:按创建时间范围过滤
        LambdaQueryWrapper<ProductInfo> pw = new LambdaQueryWrapper<>();
        applyTimeRange(pw, start, end);
        // 促销数:按创建时间范围过滤
        LambdaQueryWrapper<Promotion> mw = new LambdaQueryWrapper<>();
        applyTimeRange(mw, start, end);
        // 评价数:按创建时间范围过滤
        LambdaQueryWrapper<ProductReview> rw = new LambdaQueryWrapper<>();
        applyTimeRange(rw, start, end);
        // 会员数:按创建时间范围过滤(member 为租户级表,无门店维度)
        LambdaQueryWrapper<Member> maw = new LambdaQueryWrapper<>();
        applyTimeRange(maw, start, end);

        // 注:商品/促销/评价/会员均为租户级抽样表,无 store_id 字段,storeId 入参不参与概览计数过滤
        StatsOverviewResp resp = new StatsOverviewResp();
        resp.setProductCount(productInfoMapper.selectCount(pw));
        resp.setPromotionCount(promotionMapper.selectCount(mw));
        resp.setReviewCount(productReviewMapper.selectCount(rw));
        resp.setMemberCount(memberMapper.selectCount(maw));
        log.debug("查询统计概览(按范围) productCount={} promotionCount={} reviewCount={} memberCount={}",
                resp.getProductCount(), resp.getPromotionCount(), resp.getReviewCount(), resp.getMemberCount());
        return resp;
    }

    /** 给查询包装器附加创建时间范围过滤(起始/结束均可空) */
    private void applyTimeRange(LambdaQueryWrapper<?> wrapper, LocalDateTime start, LocalDateTime end) {
        // 实体类型不固定,用 apply 拼接原生 SQL 条件(LambdaQueryWrapper 无 String 列重载)
        if (start != null) {
            wrapper.apply("created_at >= {0}", start);
        }
        if (end != null) {
            wrapper.apply("created_at <= {0}", end);
        }
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
