package com.retail.business.dto.req;

import lombok.Data;

/**
 * 手动调整积分请求(会员详情页积分调整/Agent 调积分工具).
 * <p>对应 Controller 路由: PUT /api/v1/members/{id:\\d+}/points; {id} 由 PathVariable 正则守卫(铁律 26).
 * <p>幂等: 以 URL {id} + 前端生成幂等键为主键, 多次提交最终一致; 扣减时 Service 层校验积分余额充足.
 */
@Data
public class PointsAdjustReq {

    /** 目标会员 id, 对应 member.id; 由 Controller 路径变量覆盖; Agent 工具路径可缺省, 用 memberName/phone 反查. */
    private Long memberId;

    private String memberName;

    private String phone;

    /** 变动积分; 正数=增加, 负数=扣减, 不能为 0; 扣减时校验余额充足, 不足抛 ParamException. */
    private Integer changePoints;

    private String reason;
}
