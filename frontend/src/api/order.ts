/** order-service */
import { http } from './http'
import type { ConfirmItemReq, Order, OrderPreview, OrderStatus, PageResult, SubmitOrderReq } from '@/types/domain'

export type PreviewResp = OrderPreview & { maxPoints: number; selectedCouponId?: string }

export const orderApi = {
  preview: (body: { items: ConfirmItemReq[]; couponId?: string | null; pointsUsed?: number }) => http.post<PreviewResp>('/orders/preview', body),
  submit: (body: SubmitOrderReq) => http.post<{ orderId: string; payAmount: number; payDeadline: string }>('/orders', body),
  list: (q: { status?: OrderStatus | ''; keyword?: string; page: number; size: number }) => http.get<PageResult<Order>>('/orders', q),
  counts: () => http.get<Record<'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED', number>>('/orders/counts'),
  detail: (id: string) => http.get<Order>(`/orders/${id}`),
  cancel: (id: string) => http.post<boolean>(`/orders/${id}/cancel`),
  confirm: (id: string) => http.post<{ earnedPoints: number }>(`/orders/${id}/confirm`),
}
