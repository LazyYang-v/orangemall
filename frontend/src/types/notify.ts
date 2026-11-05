import type { LogisticsTrace, OrderStatus } from './domain'

/** WebSocket /ws/notify 推送消息体 */
export type NotifyMessage =
  | { type: 'LOGISTICS_UPDATE'; orderId: string; trace: LogisticsTrace }
  | { type: 'ORDER_STATUS'; orderId: string; status: OrderStatus; text: string }
  | { type: 'SECKILL_RESULT'; activityId: string; orderId?: string; success: boolean; reason?: string }
