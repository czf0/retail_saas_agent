package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.core.dto.PageResp;
import com.retail.business.dto.req.PromotionCreateReq;
import com.retail.business.dto.req.PromotionUpdateReq;
import com.retail.business.dto.resp.PromotionCreateResp;
import com.retail.business.dto.resp.PromotionDeleteResp;
import com.retail.business.dto.resp.PromotionListItemResp;
import com.retail.business.dto.resp.PromotionResp;
import com.retail.business.dto.resp.PromotionUpdateResp;
import com.retail.business.dto.resp.ProductPromotionItemResp;
import com.retail.business.service.PromotionService;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 促销活动管理接口.
 * <p>路由前缀 /api/v1/promotions.promotion_info 表为多租户表,tenant_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:promotion:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>支持满减 / 折扣 / 买赠三种促销类型;商品维度反查接口路径 /product/{productId} 须在 /{promotionId} 之前注册以避免占位符冲突.
 */
@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /**
     * 创建促销活动(满减 / 折扣 / 买赠等类型).
     * <p>状态由 Service 赋默认值 ACTIVE(铁律 6:CreateReq 禁 status 字段).
     */
    @PostMapping
    @SaCheckPermission("business:promotion:add")
    public R<PromotionCreateResp> create(@RequestBody PromotionCreateReq req) {
        return R.ok(promotionService.createPromotion(req));
    }

    /**
     * 分页查询促销活动列表(按状态 / 目标类型 / 关键词过滤).
     */
    @GetMapping
    @SaCheckPermission("business:promotion:query")
    public R<PageResp<PromotionListItemResp>> list(@RequestParam(required = false) Integer status,
                                                    @RequestParam(required = false) Integer targetType,
                                                    @RequestParam(required = false) String keyword) {
        return R.ok(promotionService.listPromotions(status, targetType, keyword));
    }

    /**
     * 查询促销活动详情(含适用商品列表,活动规则,时间区间).
     */
    @GetMapping("/{promotionId:\\d+}")
    @SaCheckPermission("business:promotion:query")
    public R<PromotionResp> detail(@PathVariable Long promotionId) {
        return R.ok(promotionService.getPromotion(promotionId));
    }

    /**
     * 修改促销活动(部分更新:名称 / 状态 / 规则 / 时间区间 / 适用商品).
     */
    @PutMapping("/{promotionId:\\d+}")
    @SaCheckPermission("business:promotion:edit")
    public R<PromotionUpdateResp> update(@PathVariable Long promotionId,
                                         @RequestBody PromotionUpdateReq req) {
        return R.ok(promotionService.updatePromotion(promotionId, req));
    }

    /**
     * 删除促销活动(逻辑删除,BaseServiceImpl 填充 delete_at / delete_by 审计字段).
     */
    @DeleteMapping("/{promotionId:\\d+}")
    @SaCheckPermission("business:promotion:remove")
    public R<PromotionDeleteResp> delete(@PathVariable Long promotionId) {
        return R.ok(promotionService.deletePromotion(promotionId));
    }

    /**
     * 查询某商品关联的促销活动列表(订单下单前端展示可用活动).
     * <p>此为字面量路径,须声明在 /{promotionId} 之前以保证优先匹配.
     */
    @GetMapping("/product/{productId:\\d+}")
    @SaCheckPermission("business:promotion:query")
    public R<List<ProductPromotionItemResp>> productPromotions(@PathVariable Long productId) {
        return R.ok(promotionService.getProductPromotions(productId));
    }
}
