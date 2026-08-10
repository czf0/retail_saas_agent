package com.retail.business.dto.resp;

import lombok.Data;

/**
 * 批量发券操作结果响应;包含成功发放人数 + 失败发放数(因已达限领/已发/黑名单等原因).
 * <p>幂等:同 batchNo 重复提交返回"已发放",不会重复创建 user_coupon 行;issuedCount 为累计成功总数.
 */
@Data
public class CouponIssueResp {
    /** 整体是否成功(无失败记录时为 true) */
    private Boolean success;
    /** 提示信息 */
    private String message;
    /** 成功发放数 */
    private Integer issuedCount;
    /** 失败发放数 */
    private Integer failedCount;
}
