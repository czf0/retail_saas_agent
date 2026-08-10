package com.retail.rbac.dto.resp;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店详情页展示响应;聚合门店基础信息 + 经纬度坐标 + 营业时间 + 店长绑定 + 状态(门店管理模块详情页 / POS 切换门店下拉复用).
 * <p>Controller: GET /api/v1/system/stores/{id:\\d+};{id} 正则守卫;已停用门店仍可查询(历史数据关联展示).
 */
@Data
public class StoreResp {

    private Long id;

    /** 租户外键(sys_tenant.id);门店严格归属单租户. */
    private Long tenantId;

    /** 门店名称(展示用;如"上海徐家汇店"). */
    private String storeName;

    /** 门店编码(业务唯一;对接 ERP/POS 用;如 SH001). */
    private String storeCode;

    /** 门店地址(完整文本;前端门店详情/地图跳转展示用). */
    private String address;

    /** 门店联系电话(可展示给顾客). */
    private String phone;

    /** 营业时间(文本格式 "HH:mm-HH:mm",24h;如"09:00-22:00";POS 营业开始/结束校验参考). */
    private String businessHours;

    /** 店长姓名(冗余展示;同步于 sys_user.nickName). */
    private String managerName;

    /** 店长用户外键(sys_user.id);用于审批流/消息通知 @ 店长. */
    private Long managerId;

    /** 经度(WGS84/GCJ02;APP 首页附近门店距离排序用). */
    private BigDecimal longitude;

    /** 纬度(WGS84/GCJ02;配合 longitude 计算球面距离). */
    private BigDecimal latitude;

    private String remark;

    /** 门店状态:1=ENABLED(营业中) 0=DISABLED(停用/闭店);停用门店 POS 不可选下单. */
    private Integer status;
}
