/** refund-service（并入 mall-pay，契约 §11） */
import { http } from './http'
import type { PageResult, RefundOrder } from '@/types/domain'

export const refundApi = {
  create: (body: { orderId: string; type: RefundOrder['type']; reason: string }) =>
    http.post<RefundOrder>('/refunds', body),
  list: (q?: { page?: number; size?: number }) => http.get<RefundOrder[]>('/refunds', q),
  detail: (id: string) => http.get<RefundOrder>(`/refunds/${id}`),
  cancel: (id: string) => http.post<boolean>(`/refunds/${id}/cancel`),
}

/** 运营侧 */
export const adminRefundApi = {
  list: (q?: { status?: string; page?: number; size?: number }) =>
    http.get<PageResult<RefundOrder>>('/admin/refunds', q),
  approve: (id: string, body: { pass: boolean; remark: string }) =>
    http.post<RefundOrder>(`/admin/refunds/${id}/approve`, body),
}
