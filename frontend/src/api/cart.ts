/** cart-service（Redis Hash cart:{userId}） */
import { http } from './http'
import type { CartCheckIssue, CartItem } from '@/types/domain'

export interface LocalCartItem {
  skuId: string
  count: number
  checked: boolean
}

export const cartApi = {
  list: () => http.get<CartItem[]>('/cart'),
  preview: (items: LocalCartItem[]) => http.post<CartItem[]>('/cart/preview', { items }),
  add: (skuId: string, count: number) => http.post<number>('/cart/items', { skuId, count }),
  update: (skuId: string, patch: { count?: number; checked?: boolean }) => http.put<boolean>(`/cart/items/${skuId}`, patch),
  checkAll: (checked: boolean) => http.put<boolean>('/cart/check-all', { checked }),
  remove: (skuIds: string[]) => http.delete<boolean>('/cart/items', { skuIds }),
  merge: (items: LocalCartItem[]) => http.post<number>('/cart/merge', { items }),
  check: (items: Array<{ skuId: string; count: number }>) => http.post<CartCheckIssue[]>('/cart/check', { items }),
}
