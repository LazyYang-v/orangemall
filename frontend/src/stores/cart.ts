import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { CartItem } from '@/types/domain'
import { cartApi, type LocalCartItem } from '@/api/cart'
import { KEYS, storage } from '@/utils/storage'
import { useUserStore } from './user'

/**
 * 购物车：登录态走 cart-service（Redis Hash），未登录态存 localStorage，
 * 登录成功后调用 /cart/merge 合并。
 */
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const loading = ref(false)

  const count = computed(() => items.value.length)
  const valid = computed(() => items.value.filter((i) => i.status === 'NORMAL'))
  const checked = computed(() => valid.value.filter((i) => i.checked))
  const allChecked = computed(() => valid.value.length > 0 && valid.value.every((i) => i.checked))
  const checkedAmount = computed(() => checked.value.reduce((s, i) => s + i.price * i.count, 0))
  const checkedSaved = computed(() => checked.value.reduce((s, i) => s + (i.originalPrice - i.price) * i.count, 0))
  const checkedCount = computed(() => checked.value.reduce((s, i) => s + i.count, 0))

  const isLogin = () => useUserStore().isLogin
  const guest = () => storage.get<LocalCartItem[]>(KEYS.guestCart, [])
  const saveGuest = (list: LocalCartItem[]) => storage.set(KEYS.guestCart, list)

  async function load() {
    loading.value = true
    try {
      items.value = isLogin() ? await cartApi.list() : guest().length ? await cartApi.preview(guest()) : []
    } finally {
      loading.value = false
    }
  }

  async function add(skuId: string, n = 1) {
    if (isLogin()) {
      await cartApi.add(skuId, n)
    } else {
      const list = guest()
      const hit = list.find((i) => i.skuId === skuId)
      if (hit) hit.count = Math.min(99, hit.count + n)
      else list.unshift({ skuId, count: n, checked: true })
      saveGuest(list)
    }
    await load()
  }

  async function update(skuId: string, patch: { count?: number; checked?: boolean }) {
    const item = items.value.find((i) => i.skuId === skuId)
    if (item) Object.assign(item, patch) // 乐观更新
    if (isLogin()) {
      try {
        await cartApi.update(skuId, patch)
      } catch (e) {
        await load()
        throw e
      }
    } else {
      saveGuest(guest().map((i) => (i.skuId === skuId ? { ...i, ...patch } : i)))
    }
  }

  async function checkAll(v: boolean) {
    valid.value.forEach((i) => (i.checked = v))
    if (isLogin()) await cartApi.checkAll(v)
    else saveGuest(guest().map((i) => ({ ...i, checked: v })))
  }

  async function remove(skuIds: string[]) {
    if (isLogin()) await cartApi.remove(skuIds)
    else saveGuest(guest().filter((i) => !skuIds.includes(i.skuId)))
    items.value = items.value.filter((i) => !skuIds.includes(i.skuId))
  }

  async function mergeGuestCart() {
    const list = guest()
    if (list.length) {
      await cartApi.merge(list)
      storage.remove(KEYS.guestCart)
    }
    await load()
  }

  function reset() {
    items.value = []
  }

  return {
    items, loading, count, valid, checked, allChecked, checkedAmount, checkedSaved, checkedCount,
    load, add, update, checkAll, remove, mergeGuestCart, reset,
  }
})
