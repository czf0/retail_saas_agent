// ============================================================
// 业务状态字典（与后端枚举对齐，用于 StatusTag 等组件）
// ============================================================

export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export interface StatusMeta {
  label: string
  type: TagType
}

// ---------- 订单状态（order_info.status） ----------
export const ORDER_STATUS: Record<number, StatusMeta> = {
  1: { label: '待付款', type: 'warning' },
  2: { label: '已付款', type: 'primary' },
  3: { label: '已发货', type: 'info' },
  4: { label: '已完成', type: 'success' },
  5: { label: '已关闭', type: 'danger' },
  6: { label: '退款中', type: 'warning' },
  7: { label: '已退款', type: 'danger' }
}

// ---------- 订单类型 ----------
export const ORDER_TYPE: Record<number, StatusMeta> = {
  1: { label: '正常', type: 'info' },
  2: { label: '闪购', type: 'warning' },
  3: { label: '秒杀', type: 'danger' }
}

// ---------- 订单渠道 ----------
export const ORDER_CHANNEL: Record<number, StatusMeta> = {
  1: { label: '线上', type: 'info' },
  2: { label: 'Agent', type: 'primary' },
  3: { label: '手工', type: 'warning' }
}

// ---------- 支付方式 ----------
export const PAY_TYPE: Record<number, StatusMeta> = {
  1: { label: '微信支付', type: 'success' },
  2: { label: '支付宝', type: 'primary' },
  3: { label: '余额支付', type: 'warning' },
  4: { label: '现金', type: 'info' }
}

// ---------- 退款状态（order_refund.status） ----------
export const REFUND_STATUS: Record<number, StatusMeta> = {
  1: { label: '待审核', type: 'warning' },
  2: { label: '审核通过', type: 'primary' },
  3: { label: '审核拒绝', type: 'danger' },
  4: { label: '已退款', type: 'success' }
}

// ---------- 退款类型 ----------
export const REFUND_TYPE: Record<number, StatusMeta> = {
  1: { label: '全额退款', type: 'danger' },
  2: { label: '部分退款', type: 'warning' }
}

// ---------- 商品状态 ----------
export const PRODUCT_STATUS: Record<number, StatusMeta> = {
  1: { label: '上架', type: 'success' },
  0: { label: '下架', type: 'info' }
}

// ---------- 商品分类状态 ----------
export const CATEGORY_STATUS: Record<number, StatusMeta> = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

// ---------- 评价状态（product_review.status） ----------
export const REVIEW_STATUS: Record<number, StatusMeta> = {
  1: { label: '待审核', type: 'warning' },
  2: { label: '已通过', type: 'success' },
  3: { label: '已拒绝', type: 'danger' }
}

// ---------- 促销活动状态 ----------
export const PROMOTION_STATUS: Record<number, StatusMeta> = {
  1: { label: '未开始', type: 'info' },
  2: { label: '进行中', type: 'success' },
  3: { label: '已结束', type: 'danger' }
}

// ---------- 促销类型 ----------
export const PROMOTION_TYPE: Record<number, StatusMeta> = {
  1: { label: '优惠券', type: 'primary' },
  2: { label: '折扣', type: 'success' },
  3: { label: '限时秒杀', type: 'danger' }
}

// ---------- 优惠券模板类型 ----------
export const COUPON_TYPE: Record<number, StatusMeta> = {
  1: { label: '满减券', type: 'warning' },
  2: { label: '折扣券', type: 'primary' },
  3: { label: '代金券', type: 'success' }
}

// ---------- 优惠券模板状态 ----------
export const COUPON_STATUS: Record<number, StatusMeta> = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

// ---------- 用户优惠券状态 ----------
export const USER_COUPON_STATUS: Record<number, StatusMeta> = {
  1: { label: '未使用', type: 'success' },
  2: { label: '已使用', type: 'info' },
  3: { label: '已过期', type: 'warning' },
  4: { label: '已退', type: 'danger' }
}

// ---------- 优惠券有效期类型 ----------
export const COUPON_VALID_TYPE: Record<number, StatusMeta> = {
  1: { label: '领取后N天有效', type: 'info' },
  2: { label: '固定时间段有效', type: 'primary' }
}

// ---------- 积分变动类型 ----------
export const POINTS_CHANGE_TYPE: Record<number, StatusMeta> = {
  1: { label: '消费获取', type: 'success' },
  2: { label: '活动赠送', type: 'primary' },
  3: { label: '兑换消耗', type: 'warning' },
  4: { label: '退款扣减', type: 'danger' },
  5: { label: '手动调整', type: 'info' }
}

// ---------- 库存流水类型（stock_movement.movement_type） ----------
export const STOCK_MOVEMENT_TYPE: Record<number, StatusMeta> = {
  1: { label: '入库', type: 'success' },
  2: { label: '出库', type: 'danger' },
  3: { label: '手动调整', type: 'info' },
  4: { label: '锁定', type: 'warning' },
  5: { label: '释放', type: 'info' },
  6: { label: '盘盈', type: 'success' },
  7: { label: '盘亏', type: 'danger' }
}

// ---------- 菜单类型 ----------
export const MENU_TYPE: Record<number, StatusMeta> = {
  1: { label: '目录', type: 'primary' },
  2: { label: '菜单', type: 'success' },
  3: { label: '按钮', type: 'info' }
}

// ---------- 通用启用/停用 ----------
export const ENABLE_STATUS: Record<number, StatusMeta> = {
  1: { label: '启用', type: 'success' },
  0: { label: '停用', type: 'info' }
}

// ---------- 操作日志状态（sys_oper_log.status） ----------
export const OPER_STATUS: Record<number, StatusMeta> = {
  1: { label: '正常', type: 'success' },
  0: { label: '异常', type: 'danger' }
}

// ---------- 会员等级 ----------
export const MEMBER_LEVEL: Record<number, StatusMeta> = {
  1: { label: '普通', type: 'info' },
  2: { label: '银卡', type: 'info' },
  3: { label: '金卡', type: 'warning' },
  4: { label: '钻石', type: 'primary' }
}

// ---------- 促销目标范围（对应 Java TargetType） ----------
export const TARGET_TYPE: Record<number, StatusMeta> = {
  1: { label: '全部', type: 'info' },
  2: { label: '商品', type: 'primary' },
  3: { label: '分类', type: 'warning' }
}

// ---------- 库存业务类型（对应 Java StockBizType） ----------
export const STOCK_BIZ_TYPE: Record<number, StatusMeta> = {
  1: { label: '订单业务', type: 'primary' },
  2: { label: '采购入库', type: 'success' },
  3: { label: '手动调整', type: 'warning' },
  4: { label: '退款回滚', type: 'danger' },
  5: { label: '手工操作', type: 'info' }
}

// ---------- 积分业务类型（对应 Java PointsBizType） ----------
export const POINTS_BIZ_TYPE: Record<number, StatusMeta> = {
  1: { label: '订单', type: 'primary' },
  2: { label: '优惠券', type: 'warning' },
  3: { label: '手动调整', type: 'info' },
  4: { label: '活动', type: 'success' },
  5: { label: '退款', type: 'danger' }
}

// ---------- 性别 ----------
export const GENDER_MAP: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女'
}

// ---------- 数据权限范围 ----------
export const DATA_SCOPE: Record<number, StatusMeta> = {
  1: { label: '全部', type: 'primary' },
  2: { label: '自定义', type: 'warning' },
  5: { label: '仅本人', type: 'info' }
}

/**
 * 通用获取状态元数据，未命中返回原值
 */
export function getStatusMeta(
  dict: Record<number, StatusMeta>,
  value: string | number | null | undefined
): StatusMeta {
  if (value === null || value === undefined) return { label: '-', type: 'info' }
  const num = typeof value === 'number' ? value : Number(value)
  return dict[num] || { label: String(value), type: 'info' }
}
