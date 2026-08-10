package com.retail.business.service.impl;

import com.retail.business.dto.req.ReportTimeRangeReq;
import com.retail.business.dto.resp.report.MemberGrowthResp;
import com.retail.business.dto.resp.report.MemberLevelDistResp;
import com.retail.business.dto.resp.report.MemberRfmResp;
import com.retail.business.entity.Member;
import com.retail.business.mapper.MemberMapper;
import com.retail.business.mapper.OrderInfoMapper;
import com.retail.business.service.MemberReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员报表 Service 实现.
 * <p>注入 MemberMapper / OrderInfoMapper,提供 RFM 分群 / 等级分布 / 增长趋势.
 * RFM 分群算法在 Java 层实现:查全量会员 → 计算中位数阈值 → 8 类分群.
 */
@Slf4j
@Service
public class MemberReportServiceImpl implements MemberReportService {

    /** 无下单记录的会员 R 值(设为大数表示极不活跃,100 年 ≈ 36500 天) */
    private static final int R_INFINITY = 36500;

    /** RFM 8 类分群名称,按业务重要性排序 */
    private static final List<String> SEGMENT_ORDER = Arrays.asList(
            "重要价值客户", "重要发展客户", "重要保持客户", "重要挽留客户",
            "一般价值客户", "一般发展客户", "一般保持客户", "一般挽留客户");

    private final MemberMapper memberMapper;
    private final OrderInfoMapper orderInfoMapper;

    /** 构造注入:单构造器由 Spring 自动注入全部 Mapper 依赖 */
    public MemberReportServiceImpl(MemberMapper memberMapper,
                                   OrderInfoMapper orderInfoMapper) {
        this.memberMapper = memberMapper;
        this.orderInfoMapper = orderInfoMapper;
    }

    @Override
    public List<MemberRfmResp> getRfm(ReportTimeRangeReq req) {
        // 1. 查询全量会员(tenant_id 由拦截器自动过滤)
        List<Member> members = memberMapper.selectList(null);
        if (members == null || members.isEmpty()) {
            log.debug("查询会员RFM分群 会员数为0，跳过分群计算");
            return new ArrayList<>();
        }
        log.debug("查询会员RFM分群 会员基数={}", members.size());

        // 2. 确定参考日期(R 值的计算基准日)
        LocalDate refDate = req.getEndDate() != null
                ? req.getEndDate().toLocalDate()
                : LocalDate.now();

        // 3. 计算每个会员的 R/F/M 值
        int n = members.size();
        double[] rValues = new double[n];
        double[] fValues = new double[n];
        double[] mValues = new double[n];
        for (int i = 0; i < n; i++) {
            Member m = members.get(i);
            // R = 最后下单日期至参考日的天数(越小越活跃);无下单记录设为 R_INFINITY
            if (m.getLastOrderAt() != null) {
                rValues[i] = ChronoUnit.DAYS.between(m.getLastOrderAt().toLocalDate(), refDate);
            } else {
                rValues[i] = R_INFINITY;
            }
            // F = 累计订单数
            fValues[i] = m.getTotalOrders() != null ? m.getTotalOrders() : 0;
            // M = 累计消费金额
            mValues[i] = m.getTotalSpent() != null ? m.getTotalSpent().doubleValue() : 0;
        }

        // 4. 计算中位数阈值
        double rMedian = median(rValues);
        double fMedian = median(fValues);
        double mMedian = median(mValues);

        // 5. 按 R/F/M 高低分群并聚合
        Map<String, List<Member>> segmentMap = new LinkedHashMap<>();
        for (String seg : SEGMENT_ORDER) {
            segmentMap.put(seg, new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            Member m = members.get(i);
            // R 高 = 近期有消费(天数 <= 中位数);R 低 = 较久未消费
            boolean rHigh = rValues[i] <= rMedian;
            // F 高 = 频次 >= 中位数;F 低 = 频次 < 中位数
            boolean fHigh = fValues[i] >= fMedian;
            // M 高 = 金额 >= 中位数;M 低 = 金额 < 中位数
            boolean mHigh = mValues[i] >= mMedian;
            String segment = classifySegment(rHigh, fHigh, mHigh);
            segmentMap.get(segment).add(m);
        }

        // 6. 构建响应(仅返回有会员的客群)
        List<MemberRfmResp> result = new ArrayList<>();
        for (String seg : SEGMENT_ORDER) {
            List<Member> segMembers = segmentMap.get(seg);
            if (segMembers.isEmpty()) {
                continue;
            }
            MemberRfmResp resp = new MemberRfmResp();
            resp.setSegment(seg);
            resp.setMemberCount(segMembers.size());
            resp.setPercentage(calcPercentage(segMembers.size(), n));
            // 平均消费金额 = 该客群会员 totalSpent 之和 / 客群人数
            BigDecimal totalSpent = segMembers.stream()
                    .map(m -> m.getTotalSpent() != null ? m.getTotalSpent() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            resp.setAvgSpent(totalSpent.divide(BigDecimal.valueOf(segMembers.size()), 2, RoundingMode.HALF_UP));
            result.add(resp);
        }
        log.debug("查询会员RFM分群完成 rMedian={} fMedian={} mMedian={} 客群数={}",
                rMedian, fMedian, mMedian, result.size());
        return result;
    }

    @Override
    public List<MemberLevelDistResp> getLevelDist(ReportTimeRangeReq req) {
        log.debug("查询会员等级分布");
        List<MemberLevelDistResp> list = memberMapper.selectLevelDist();
        // 计算各等级占总会员数百分比
        int total = list.stream().mapToInt(MemberLevelDistResp::getMemberCount).sum();
        list.forEach(item -> item.setPercentage(calcPercentage(item.getMemberCount(), total)));
        log.debug("查询会员等级分布完成 等级数={} totalMembers={}", list.size(), total);
        return list;
    }

    @Override
    public List<MemberGrowthResp> getGrowth(ReportTimeRangeReq req) {
        log.debug("查询会员增长趋势 startDate={} endDate={}", req.getStartDate(), req.getEndDate());
        // 1. 查询每日新增会员数
        List<MemberGrowthResp> newMembers = memberMapper.selectMemberGrowth(
                req.getStartDate(), req.getEndDate());
        // 2. 查询每日活跃会员数
        List<MemberGrowthResp> activeMembers = orderInfoMapper.selectActiveMemberByDate(
                req.getStartDate(), req.getEndDate());
        // 3. 按日期合并两份数据
        Map<String, MemberGrowthResp> dateMap = new LinkedHashMap<>();
        for (MemberGrowthResp item : newMembers) {
            MemberGrowthResp merged = new MemberGrowthResp();
            merged.setDate(item.getDate());
            merged.setNewMembers(item.getNewMembers() != null ? item.getNewMembers() : 0);
            merged.setActiveMembers(0);
            dateMap.put(item.getDate(), merged);
        }
        for (MemberGrowthResp item : activeMembers) {
            MemberGrowthResp merged = dateMap.get(item.getDate());
            if (merged == null) {
                merged = new MemberGrowthResp();
                merged.setDate(item.getDate());
                merged.setNewMembers(0);
                dateMap.put(item.getDate(), merged);
            }
            merged.setActiveMembers(item.getActiveMembers() != null ? item.getActiveMembers() : 0);
        }
        // 按日期排序返回
        List<MemberGrowthResp> result = dateMap.values().stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());
        log.debug("查询会员增长趋势完成 数据点={} newMemberDays={} activeMemberDays={}",
                result.size(), newMembers.size(), activeMembers.size());
        return result;
    }

    // ===================== RFM 辅助方法 =====================

    /**
     * 计算数组中位数(排序后取中间值).
     */
    private double median(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        } else {
            return sorted[mid];
        }
    }

    /**
     * 根据 R/F/M 高低组合映射到 8 类客群名称.
     * <p>
     * R高F高M高=重要价值 / R高F低M高=重要发展 / R低F高M高=重要保持 / R低F低M高=重要挽留
     * R高F高M低=一般价值 / R高F低M低=一般发展 / R低F高M低=一般保持 / R低F低M低=一般挽留
     */
    private String classifySegment(boolean rHigh, boolean fHigh, boolean mHigh) {
        if (rHigh && fHigh && mHigh) return "重要价值客户";
        if (rHigh && !fHigh && mHigh) return "重要发展客户";
        if (!rHigh && fHigh && mHigh) return "重要保持客户";
        if (!rHigh && !fHigh && mHigh) return "重要挽留客户";
        if (rHigh && fHigh && !mHigh) return "一般价值客户";
        if (rHigh && !fHigh && !mHigh) return "一般发展客户";
        if (!rHigh && fHigh && !mHigh) return "一般保持客户";
        return "一般挽留客户";
    }

    /**
     * 计算百分比 = count / total × 100,保留 2 位小数.
     */
    private BigDecimal calcPercentage(int count, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
