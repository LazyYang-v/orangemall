import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserProfile } from '@/types/domain'
import { userApi } from '@/api/user'
import { KEYS, storage } from '@/utils/storage'
import { useCartStore } from './cart'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(storage.get<string | null>(KEYS.token, null))
  const profile = ref<UserProfile | null>(null)
  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(() => profile.value?.role === 'ADMIN')

  async function afterLogin(t: string, user: UserProfile) {
    token.value = t
    profile.value = user
    storage.set(KEYS.token, t)
    // 未登录购物车合并到 Redis
    await useCartStore().mergeGuestCart()
  }

  async function login(body: { phone: string; password?: string; smsCode?: string }) {
    const r = await userApi.login(body)
    await afterLogin(r.token, r.user)
  }

  async function register(body: { phone: string; password: string; nickname?: string; smsCode: string }) {
    const r = await userApi.register(body)
    await afterLogin(r.token, r.user)
  }

  async function fetchProfile() {
    if (!token.value) return
    try {
      profile.value = await userApi.me()
    } catch {
      /* 401 已由拦截器处理 */
    }
  }

  function clear() {
    token.value = null
    profile.value = null
    storage.remove(KEYS.token)
    useCartStore().reset()
  }

  async function logout() {
    try {
      await userApi.logout()
    } finally {
      clear()
    }
  }

  return { token, profile, isLogin, isAdmin, login, register, fetchProfile, logout, clear }
})
