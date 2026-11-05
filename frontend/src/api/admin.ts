/** 运营后台（admin-service / 各服务 admin 接口） */
import { http } from './http'
import type {
  CacheCheckRow, Category, CouponTemplate, DashboardStats, Order, OrderStatus, PageResult, RiskUser, SeckillItem, SeckillSession, Spu,
} from '@/types/domain'

export interface AdminSeckillItem extends SeckillItem {
  warmed: boolean
  redisStock: number | null
  dbStock: number | null
}

export interface SeckillMetrics {
  totalRequests: number
  rateLimited: number
  illegalPath: number
  repeatBlocked: number
  soldOut: number
  enqueued: number
  ordersCreated: number
  optimisticFail: number
}

export interface OpsLog {
  time: string
  type: 'CACHE' | 'MQ' | 'PAY' | 'SECKILL' | 'ORDER' | 'RISK'
  text: string
}

export const adminApi = {
  dashboard: () => http.get<DashboardStats>('/admin/dashboard'),
  orders: (q: { status?: OrderStatus | ''; source?: string; keyword?: string; page: number; size: number }) => http.get<PageResult<Order>>('/admin/orders', q),
  ship: (id: string, body: { company: string; trackingNo?: string }) => http.post<boolean>(`/admin/orders/${id}/ship`, body),
  expire: (id: string) => http.post<boolean>(`/admin/orders/${id}/expire`),
  products: (q: { keyword?: string; status?: string }) => http.get<Spu[]>('/admin/products', q),
  setProductStatus: (id: string, status: Spu['status']) => http.put<boolean>(`/admin/products/${id}/status`, { status }),
  updateSku: (id: string, body: { price?: number; stock?: number }) => http.put<boolean>(`/admin/skus/${id}`, body),
  categories: () => http.get<Category[]>('/admin/categories'),
  addCategory: (body: { parentId: number; name: string }) => http.post<Category>('/admin/categories', body),
  renameCategory: (id: number, name: string) => http.put<boolean>(`/admin/categories/${id}`, { name }),
  deleteCategory: (id: number) => http.delete<boolean>(`/admin/categories/${id}`),
  coupons: () => http.get<CouponTemplate[]>('/admin/coupons'),
  createCoupon: (body: Partial<CouponTemplate>) => http.post<CouponTemplate>('/admin/coupons', body),
  seckill: () => http.get<{ sessions: Array<SeckillSession & { items: AdminSeckillItem[] }>; metrics: SeckillMetrics }>('/admin/seckill'),
  warmUp: (activityId: string) => http.post<boolean>(`/admin/seckill/${activityId}/warmup`),
  simulate: (activityId: string, users: number) =>
    http.post<{ requests: number; success: number; soldOut: number; limited: number; redisLeft: number; dbLeft: number }>(`/admin/seckill/${activityId}/simulate`, { users }),
  cacheCheck: () => http.get<{ rows: CacheCheckRow[]; hitRate: number; hit: number; miss: number }>('/admin/cache/check'),
  cacheRepair: (key: string) => http.post<boolean>('/admin/cache/repair', { key }),
  risk: () => http.get<RiskUser[]>('/admin/risk'),
  setRisk: (userId: string, blocked: boolean) => http.put<boolean>(`/admin/risk/${userId}`, { blocked }),
  logs: (type?: string) => http.get<OpsLog[]>('/admin/logs', { type }),
  resetMock: () => http.post<boolean>('/admin/mock/reset'),
}
