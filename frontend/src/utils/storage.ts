export const storage = {
  get<T>(key: string, fallback: T): T {
    try {
      const raw = localStorage.getItem(key)
      return raw ? (JSON.parse(raw) as T) : fallback
    } catch {
      return fallback
    }
  },
  set(key: string, value: unknown) {
    localStorage.setItem(key, JSON.stringify(value))
  },
  remove(key: string) {
    localStorage.removeItem(key)
  },
}

export const KEYS = {
  token: 'om_token',
  guestCart: 'om_guest_cart', // 未登录购物车（登录后合并到 Redis）
  mockDb: 'om_mock_db_v1',
  history: 'om_search_history',
} as const
