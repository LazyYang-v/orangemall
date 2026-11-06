import type { OrderStatus, PayChannel, LogisticsState, CouponType, SeckillStatus } from '@/types/domain'

export const ORDER_STATUS: Record<OrderStatus, { text: string; color: string }> = {
  PENDING_PAYMENT: { text: '待付款', color: '#ff5000' },
  PAID: { text: '待发货', color: '#2f7cf6' },
  SHIPPED: { text: '待收货', color: '#8a4dff' },
  COMPLETED: { text: '已完成', color: '#1aa35b' },
  CANCELLED: { text: '已取消', color: '#999' },
  CLOSED: { text: '已关闭', color: '#999' },
  REFUND_APPLYING: { text: '售后处理中', color: '#e6a23c' },
  REFUNDED: { text: '已退款', color: '#ff3a3a' },
}

export const REFUND_STATUS: Record<string, { text: string; color: string }> = {
  WAIT_APPROVAL: { text: '待审批', color: '#e6a23c' },
  APPROVED: { text: '审批通过', color: '#2f7cf6' },
  REFUNDING: { text: '退款中', color: '#2f7cf6' },
  DONE: { text: '已退款', color: '#1aa35b' },
  REJECTED: { text: '已拒绝', color: '#999' },
  CANCELLED: { text: '用户已取消', color: '#999' },
}

export const REFUND_TYPE: Record<string, string> = {
  REFUND: '仅退款',
  RETURN: '退货退款',
  EXCHANGE: '换货',
}

export const PAY_CHANNEL: Record<PayChannel, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
}

export const LOGISTICS_STATE: Record<LogisticsState, string> = {
  COLLECTED: '已揽收',
  IN_TRANSIT: '运输中',
  DELIVERING: '派送中',
  SIGNED: '已签收',
}

export const COUPON_TYPE: Record<CouponType, string> = {
  FULL_REDUCTION: '满减券',
  DISCOUNT: '折扣券',
  NO_THRESHOLD: '无门槛',
}

export const SECKILL_STATUS: Record<SeckillStatus, string> = {
  UPCOMING: '即将开抢',
  ONGOING: '抢购中',
  ENDED: '已结束',
}
