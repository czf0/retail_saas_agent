package com.retail.business.dto.resp;

import com.retail.business.dto.OperationResultResp;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品批量操作结果响应(批量上/下架/启用停用);包含成功/失败/跳过计数 + 每条商品明细(Agent/HITL 逐条回述用户确认).
 * <p>原子性:非事务整体回滚;单条失败不影响其余条目执行;最终 items 列表可按顺序定位哪条失败及原因.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductBatchActionResp extends OperationResultResp {

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failedCount;

    /** 跳过条数(已是目标状态) */
    private Integer skippedCount;

    /** 每条商品明细(成功/跳过会有;失败则记录 reason) */
    private List<Item> items;

    @Data
    public static class Item {
        /** 商品ID */
        private Long productId;
        /** 商品名 */
        private String name;
        /** 当前售价(展示给用户确认) */
        private BigDecimal price;
        /** 当前库存(展示给用户确认) */
        private Integer stockQty;
        /** 变更前状态文案(上架/下架) */
        private String beforeStatus;
        /** 变更后状态文案 */
        private String afterStatus;
        /** SKIP/FAILED 时的原因 */
        private String reason;
    }
}
