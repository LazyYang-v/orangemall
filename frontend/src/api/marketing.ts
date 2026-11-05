/** marketing-service：优惠券 / 积分 */
import { http } from './http'
import type { CouponTemplate, PointsGoods, PointsRecord, UserCoupon, UserCouponStatus } from '@/types/domain'

export const couponApi = {
  templates: () => http.get<CouponTemplate[]>('/coupons/templates'),
  receive: (templateId: string) => http.post<UserCoupon>(`/coupons/templates/${templateId}/receive`),
  mine: (status?: UserCouponStatus) => http.get<UserCoupon[]>('/coupons/mine', { status }),
}

export const pointsApi = {
  summary: () => http.get<{ balance: number; records: PointsRecord[] }>('/points'),
  goods: () => http.get<PointsGoods[]>('/points/goods'),
  exchange: (id: string) => http.post<{ balance: number }>(`/points/goods/${id}/exchange`),
}
