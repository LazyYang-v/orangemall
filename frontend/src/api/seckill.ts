/** seckill-service */
import { http } from './http'
import type { SeckillItem, SeckillResult, SeckillSession } from '@/types/domain'

export const seckillApi = {
  sessions: () => http.get<Array<SeckillSession & { items: SeckillItem[] }>>('/seckill/sessions'),
  item: (activityId: string) => http.get<SeckillItem & { bought: boolean; serverTime: number }>(`/seckill/items/${activityId}`),
  /** 1. 获取动态秒杀地址（防止提前拿到接口刷单） */
  path: (activityId: string) => http.get<{ path: string }>(`/seckill/${activityId}/path`),
  /** 2. 用动态地址发起秒杀 → Redis Lua 扣减 → Kafka，返回排队中 */
  submit: (activityId: string, path: string, addressId?: string) => http.post<SeckillResult>(`/seckill/${activityId}/${path}/orders`, { addressId }),
  /** 3. 轮询结果 */
  result: (activityId: string) => http.get<SeckillResult>(`/seckill/${activityId}/result`),
}
