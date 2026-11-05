/** user-service */
import { http } from './http'
import type { Address, UserProfile } from '@/types/domain'

export interface LoginResp {
  token: string
  user: UserProfile
}

export const userApi = {
  login: (body: { phone: string; password?: string; smsCode?: string }) => http.post<LoginResp>('/auth/login', body),
  register: (body: { phone: string; password: string; nickname?: string; smsCode: string }) => http.post<LoginResp>('/auth/register', body),
  sendSms: (phone: string) => http.post<{ sent: boolean; hint?: string }>('/auth/sms', { phone }),
  logout: () => http.post<boolean>('/auth/logout'),
  me: () => http.get<UserProfile>('/users/me'),
  addresses: () => http.get<Address[]>('/addresses'),
  saveAddress: (a: Partial<Address>) => http.post<Address>('/addresses', a),
  deleteAddress: (id: string) => http.delete<boolean>(`/addresses/${id}`),
}
