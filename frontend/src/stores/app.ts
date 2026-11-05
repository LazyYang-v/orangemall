import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Category } from '@/types/domain'
import { productApi } from '@/api/product'

/** 全局只读数据：分类树（后端可走本地缓存 Caffeine + Redis 二级缓存） */
export const useAppStore = defineStore('app', () => {
  const categories = ref<Category[]>([])
  let pending: Promise<void> | undefined

  function loadCategories(force = false) {
    if (categories.value.length && !force) return Promise.resolve()
    pending ||= productApi.categoryTree().then((list) => {
      categories.value = list
    }).finally(() => (pending = undefined))
    return pending
  }

  function findPath(id: number): Category[] {
    const walk = (list: Category[], trail: Category[]): Category[] | undefined => {
      for (const c of list) {
        const t = [...trail, c]
        if (c.id === id) return t
        const hit = c.children && walk(c.children, t)
        if (hit) return hit
      }
      return undefined
    }
    return walk(categories.value, []) ?? []
  }

  return { categories, loadCategories, findPath }
})
