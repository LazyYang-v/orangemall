import type { OrderStatus, PayChannel, LogisticsState, CouponType, SeckillStatus } from '@/types/domain'

export const ORDER_STATUS: Record<OrderStatus, { text: string; color: string }> = {
  PENDING_PAYMENT: { text: '待付款', color: '#ff5000' },
  PAID: { text: '待发货', color: '#2f7cf6' },
  SHIPPED: { text: '待收货', color: '#8a4dff' },
  COMPLETED: { text: '已完成', color: '#1aa35b' },
  CANCELLED: { text: '已取消', color: '#999' },
  CLOSED: { text: '已关闭', color: '#999' },
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
