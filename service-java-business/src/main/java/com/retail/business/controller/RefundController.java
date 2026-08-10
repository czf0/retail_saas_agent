package com.retail.business.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.retail.business.dto.req.RefundAuditReq;
import com.retail.business.dto.req.RefundCreateReq;
import com.retail.business.dto.resp.RefundAuditResp;
import com.retail.business.dto.resp.RefundListItemResp;
import com.retail.business.dto.resp.RefundResp;
import com.retail.business.service.RefundService;
import com.retail.core.dto.PageResp;
import com.retail.core.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款单管理接口.
 * <p>路由前缀 /api/v1/refunds.refund 表为多租户 + 门店隔离表,
 * tenant_id / store_id 由拦截器自动按当前登录用户上下文隔离.
 * <p>权限校验基于 @SaCheckPermission("business:refund:*") 注解(AOP),
 * 对应 sys_menu F 型按钮 perms 字段(见 init_tables.sql 业务管理菜单种子).
 * <p>审核通过接口触发跨模块联动(退券 / 退积分 / 库存回滚 / 订单 refund_amount 累加),
 * 整体包裹事务保证数据一致性.
 */
@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    /** 构造注入:单构造器由 Spring 自动注入,依赖不可变且便于单元测试. */
    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * 创建退款申请.
     */
    @PostMapping
    @SaCheckPermission("business:refund:audit")
    public R<RefundResp> create(@RequestBody RefundCreateReq req) {
        return R.ok(refundService.createRefund(req));
    }

    /**
     * 分页查询退款单列表.
     */
    @GetMapping
    @SaCheckPermission("business:refund:query")
    public R<PageResp<RefundListItemResp>> list(@RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        return R.ok(refundService.listRefunds(status, orderNo, startDate, endDate));
    }

    /**
     * 查询退款单详情.
     */
    @GetMapping("/{refundId:\\d+}")
    @SaCheckPermission("business:refund:query")
    public R<RefundResp> detail(@PathVariable Long refundId) {
        return R.ok(refundService.getRefund(refundId));
    }

    /**
     * 审核退款单(通过/拒绝,通过时触发跨模块退款联动).
     */
    @PostMapping("/{refundId:\\d+}/audit")
    @SaCheckPermission("business:refund:audit")
    public R<RefundAuditResp> audit(@PathVariable Long refundId,
                                     @RequestBody RefundAuditReq req) {
        return R.ok(refundService.auditRefund(refundId, req));
    }
}
