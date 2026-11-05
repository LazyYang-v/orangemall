/** pay-service */
import { http } from './http'
import type { PayChannel, PayOrderVO, PayStatus } from '@/types/domain'

export interface SandboxNotify {
  paymentNo: string
  amount: number
  tradeStatus: string
  sign: string
}

export const payApi = {
  create: (orderId: string, channel: PayChannel) => http.post<PayOrderVO>('/payments', { orderId, channel }),
  status: (paymentNo: string) => http.get<{ status: PayStatus; orderId: string; callbackCount: number }>(`/payments/${paymentNo}`),
  /** 沙箱：模拟用户扫码后，支付平台生成的带签名回调报文 */
  sandboxPay: (paymentNo: string, success: boolean) => http.post<SandboxNotify>(`/payments/${paymentNo}/sandbox-pay`, { success }),
  /** 支付平台 → 我方回调（真实环境由支付宝/微信服务器调用，此处仅用于沙箱演示） */
  notify: (body: SandboxNotify) => http.post<{ result: string; idempotent?: boolean; callbackCount?: number }>('/payments/notify', body),
}
