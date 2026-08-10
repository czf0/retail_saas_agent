package com.retail.business.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.retail.core.enums.BaseEnum;

/**
 * 商品评价审核状态枚举; code = 1 待审核, 2 已通过, 3 已拒绝.
 * <p>状态流转(触发动作 + 允许前置态):
 * <ol>
 *   <li>PENDING(1) → APPROVED(2): 运营在评价后台审核通过; 评价展示于前台商品详情页; 若有配置则发放用户积分.</li>
 *   <li>PENDING(1) → REJECTED(3): 运营审核拒绝(内容违规 / 低俗 / 虚假信息); 用户通过站内信通知; 用户可修改后重新提交(创建 NEW 评价行).</li>
 *   <li>APPROVED(2) → REJECTED(3): 发布后撤下(投诉 / 举报核实); 前台隐藏; 复审后可恢复至 APPROVED.</li>
 * </ol>
 * <p>带图评价默认走审核; 平台自营商品可配置免审(提交时直接置为 APPROVED).
 */
public enum ReviewStatus implements BaseEnum {

    /** 待审核(用户提交评价后初始态); 前台公开不展示; 仅运营后台审核员可见. */
    PENDING(1, "待审核"),
    /** 已通过(前台可见); 评价展示于商品详情页; 可点击有用投票; 图文评价用户可获得积分奖励. */
    APPROVED(2, "已通过"),
    /** 已拒绝(前台不展示); 用户收到站内信通知并附拒绝原因; 用户可修改内容后作为 NEW 评价记录重新提交. */
    REJECTED(3, "已拒绝");

    @EnumValue
    private final Integer code;
    private final String desc;

    ReviewStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
