package com.retail.business.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.retail.core.service.BaseServiceImpl;
import com.retail.business.convert.MemberTagConvert;
import com.retail.core.context.PageContextHolder;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.MemberTagAssignReq;
import com.retail.business.dto.req.MemberTagReq;
import com.retail.business.dto.resp.MemberTagResp;
import com.retail.business.entity.MemberTag;
import com.retail.business.entity.MemberTagRel;
import com.retail.business.mapper.MemberTagMapper;
import com.retail.business.mapper.MemberTagRelMapper;
import com.retail.business.service.MemberTagService;
import com.retail.core.exception.ParamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员标签服务实现.
 * <p>
 * 继承 {@link BaseServiceImpl} 获得逻辑删除能力(MemberTag 为逻辑删除表);
 * MemberTagRel 为物理删除关系表,通过注入的 {@link MemberTagRelMapper} 直接操作.
 * <p>
 * assignTags 采用「先查现有关系 → 过滤已存在 → 批量插入新关系」的去重策略,
 * 避免违反 member_tag_rel 的 UNIQUE KEY uk_member_tag 约束.
 */
@Slf4j
@Service
public class MemberTagServiceImpl extends BaseServiceImpl<MemberTagMapper, MemberTag> implements MemberTagService {

    private final MemberTagRelMapper memberTagRelMapper;
    private final MemberTagConvert memberTagConvert;

    /**
     * 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试.
     * <p>baseMapper(即 MemberTagMapper)由 {@link BaseServiceImpl} 基于泛型自动注入,无需重复声明.
     * <p>memberTagRelMapper 用于标签 - 会员关系的分配 / 取消 / 查询(物理删除关系表,不含 deleted 字段);
     * memberTagConvert 用于 MemberTag → MemberTagResp 转换(color 枚举自动映射).
     */
    public MemberTagServiceImpl(MemberTagRelMapper memberTagRelMapper, MemberTagConvert memberTagConvert) {
        this.memberTagRelMapper = memberTagRelMapper;
        this.memberTagConvert = memberTagConvert;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberTagResp createTag(MemberTagReq req) {
        if (StrUtil.isBlank(req.getTagName())) {
            throw new ParamException("标签名称不能为空");
        }
        String name = req.getTagName().trim();
        // 校验租户内名称唯一(tenant_id 由拦截器自动附加到查询条件)
        Long dup = baseMapper.selectCount(
                new LambdaQueryWrapper<MemberTag>().eq(MemberTag::getTagName, name));
        if (dup != null && dup > 0) {
            throw new ParamException("标签名称已存在");
        }

        // 同名字段由 MemberTagConvert 自动映射(req→entity)
        MemberTag entity = memberTagConvert.toEntity(req);
        entity.setTagName(name);
        save(entity);
        log.info("创建会员标签 tagId={} tagName={} tagColor={}", entity.getId(), entity.getTagName(), entity.getTagColor());
        return memberTagConvert.toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberTagResp updateTag(Long tagId, MemberTagReq req) {
        MemberTag tag = getById(tagId);
        if (tag == null) {
            throw new ParamException("标签不存在");
        }

        if (StrUtil.isNotBlank(req.getTagName())) {
            String name = req.getTagName().trim();
            if (!name.equals(tag.getTagName())) {
                // 校验租户内名称唯一(排除自身)
                Long dup = baseMapper.selectCount(
                        new LambdaQueryWrapper<MemberTag>()
                                .ne(MemberTag::getId, tagId)
                                .eq(MemberTag::getTagName, name));
                if (dup != null && dup > 0) {
                    throw new ParamException("标签名称已存在");
                }
                tag.setTagName(name);
            }
        }
        if (req.getTagColor() != null) {
            tag.setTagColor(req.getTagColor());
        }
        if (req.getDescription() != null) {
            tag.setDescription(req.getDescription());
        }
        updateById(tag);
        log.info("更新会员标签 tagId={} tagName={} tagColor={} description={}",
                tag.getId(), tag.getTagName(), tag.getTagColor(), tag.getDescription());
        return memberTagConvert.toResp(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTag(Long tagId) {
        MemberTag tag = getById(tagId);
        if (tag == null) {
            throw new ParamException("标签不存在");
        }
        // 逻辑删除标签定义(BaseServiceImpl 填充 deleteAt/deleteBy)
        removeById(tagId);
        // 物理删除该标签的所有会员关系
        int relDeleted = memberTagRelMapper.delete(
                new LambdaQueryWrapper<MemberTagRel>().eq(MemberTagRel::getTagId, tagId));
        log.info("删除会员标签 tagId={} tagName={} 关联关系删除数={}", tagId, tag.getTagName(), relDeleted);
        return true;
    }

    @Override
    public List<MemberTagResp> listTags(String keyword) {
        LambdaQueryWrapper<MemberTag> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(MemberTag::getTagName, keyword);
        }
        wrapper.orderByDesc(MemberTag::getCreatedAt);
        List<MemberTag> tags = list(wrapper);
        List<MemberTagResp> resps = memberTagConvert.toRespList(tags);
        // 批量统计每个标签的会员数(GROUP BY tag_id 单次查询,避免逐标签 N+1)
        // 直接使用注入的 MemberTagRelMapper 而非其他 Service,遵循 "Service 层引用 Mapper" 依赖规范
        fillMemberCount(resps);
        return resps;
    }

    /**
     * 批量回填标签下会员数.
     * <p>通过 member_tag_rel 表 GROUP BY tag_id 单次聚合查询,构建 tagId→count 映射后统一回填,
     * 避免逐标签 selectCount 的 N+1 查询问题.空标签列表直接返回.
     *
     * @param resps 标签响应列表 (原地修改 memberCount 字段)
     */
    private void fillMemberCount(List<MemberTagResp> resps) {
        if (CollUtil.isEmpty(resps)) {
            return;
        }
        Set<Long> tagIds = resps.stream()
                .map(MemberTagResp::getId)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return;
        }
        // selectMaps + GROUP BY:返回 [{tag_id: 1, cnt: 12}, ...],单次查询完成全量统计
        QueryWrapper<MemberTagRel> qw = new QueryWrapper<>();
        qw.select("tag_id", "count(*) as cnt")
                .in("tag_id", tagIds)
                .groupBy("tag_id");
        List<Map<String, Object>> counts = memberTagRelMapper.selectMaps(qw);
        Map<Long, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : counts) {
            Object tagIdVal = row.get("tag_id");
            Object cntVal = row.get("cnt");
            if (tagIdVal instanceof Number && cntVal instanceof Number) {
                countMap.put(((Number) tagIdVal).longValue(), ((Number) cntVal).longValue());
            }
        }
        // 未出现在聚合结果中的标签会员数为 0
        resps.forEach(r -> r.setMemberCount(countMap.getOrDefault(r.getId(), 0L)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignTags(MemberTagAssignReq req) {
        Long memberId = req.getMemberId();
        List<Long> tagIds = req.getTagIds();
        if (CollUtil.isEmpty(tagIds)) {
            return 0;
        }
        // 铁律 12:批量操作单批上限 50,避免 UNIQUE KEY 校验过多锁行时间过长
        if (tagIds.size() > 50) {
            throw new ParamException("单批分配标签上限50，请分批发");
        }

        // 查询该会员已存在的标签关系,用于去重
        List<MemberTagRel> existing = memberTagRelMapper.selectList(
                new LambdaQueryWrapper<MemberTagRel>()
                        .eq(MemberTagRel::getMemberId, memberId)
                        .in(MemberTagRel::getTagId, tagIds));
        Set<Long> existingTagIds = existing.stream()
                .map(MemberTagRel::getTagId)
                .collect(Collectors.toSet());

        // 过滤掉已存在的标签ID,去重后构建新关系
        List<MemberTagRel> toInsert = tagIds.stream()
                .distinct()
                .filter(tid -> !existingTagIds.contains(tid))
                .map(tid -> {
                    MemberTagRel rel = new MemberTagRel();
                    rel.setMemberId(memberId);
                    rel.setTagId(tid);
                    return rel;
                })
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(toInsert)) {
            log.debug("分配会员标签跳过 memberId={} tagIds={} 原因=全部已存在", memberId, tagIds);
            return 0;
        }
        // 逐条插入(tenant_id 由拦截器自动注入)
        for (MemberTagRel rel : toInsert) {
            memberTagRelMapper.insert(rel);
        }
        log.info("分配会员标签 memberId={} 请求标签数={} 实际新增={}", memberId, tagIds.size(), toInsert.size());
        return toInsert.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeTags(Long memberId, List<Long> tagIds) {
        if (CollUtil.isEmpty(tagIds)) {
            return 0;
        }
        // 铁律 12:批量操作单批上限 50,避免 IN 子句过长与锁行范围过大
        if (tagIds.size() > 50) {
            throw new ParamException("单批取消标签上限50，请分批发");
        }
        int deleted = memberTagRelMapper.delete(
                new LambdaQueryWrapper<MemberTagRel>()
                        .eq(MemberTagRel::getMemberId, memberId)
                        .in(MemberTagRel::getTagId, tagIds));
        log.info("移除会员标签 memberId={} tagIds={} 实际删除={}", memberId, tagIds, deleted);
        return deleted;
    }

    @Override
    public List<MemberTagResp> listMemberTags(Long memberId) {
        // 查询会员的所有标签关系
        List<MemberTagRel> rels = memberTagRelMapper.selectList(
                new LambdaQueryWrapper<MemberTagRel>().eq(MemberTagRel::getMemberId, memberId));
        if (CollUtil.isEmpty(rels)) {
            return Collections.emptyList();
        }
        // 查询标签详情(listByIds 由 IService 提供,自动附加租户隔离 + 逻辑删除过滤)
        List<Long> tagIds = rels.stream()
                .map(MemberTagRel::getTagId)
                .collect(Collectors.toList());
        List<MemberTag> tags = listByIds(tagIds);
        return memberTagConvert.toRespList(tags);
    }

    @Override
    public PageResp<Long> listTagMembers(Long tagId) {
        LambdaQueryWrapper<MemberTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemberTagRel::getTagId, tagId);

        // 分页参数由 PageParameterInterceptor 从 request 注入 ThreadLocal;
        // selectPage 内部自动执行 count + 分页查询(一次调用,SQL 由分页插件拼接,无需手动 selectCount + last("LIMIT"))
        Page<MemberTagRel> page = PageContextHolder.get();
        IPage<MemberTagRel> result = memberTagRelMapper.selectPage(page, wrapper);

        List<Long> memberIds = result.getRecords().stream()
                .map(MemberTagRel::getMemberId)
                .collect(Collectors.toList());
        return new PageResp<>(memberIds, result.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }
}
