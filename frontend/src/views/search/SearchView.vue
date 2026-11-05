<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Brand, SearchQuery, SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import { useAppStore } from '@/stores/app'
import ProductCard from '@/components/common/ProductCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import Empty from '@/components/common/Empty.vue'
import Icon from '@/components/common/Icon.vue'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const list = ref<SpuCard[]>([])
const brands = ref<Brand[]>([])
const total = ref(0)
const loading = ref(false)
const priceMin = ref('')
const priceMax = ref('')

const q = computed<SearchQuery>(() => ({
  keyword: (route.query.keyword as string) || undefined,
  categoryId: route.query.categoryId ? Number(route.query.categoryId) : undefined,
  brandId: route.query.brandId ? Number(route.query.brandId) : undefined,
  sort: (route.query.sort as SearchQuery['sort']) || 'default',
  minPrice: route.query.minPrice ? Number(route.query.minPrice) : undefined,
  maxPrice: route.query.maxPrice ? Number(route.query.maxPrice) : undefined,
  page: Number(route.query.page || 1),
  size: 20,
}))

const path = computed(() => (q.value.categoryId ? app.findPath(q.value.categoryId) : []))
const subCats = computed(() => {
  const last = path.value[path.value.length - 1]
  if (!last) return app.categories
  return last.children?.length ? last.children : (path.value[path.value.length - 2]?.children ?? [])
})

async function load() {
  loading.value = true
  try {
    const r = await productApi.search(q.value)
    list.value = r.records
    total.value = r.total
    brands.value = r.brands
  } finally {
    loading.value = false
  }
}

function patch(p: Record<string, string | number | undefined>) {
  const next: Record<string, string> = {}
  Object.entries({ ...route.query, page: undefined, ...p }).forEach(([k, v]) => {
    if (v !== undefined && v !== '' && v !== null) next[k] = String(v)
  })
  router.push({ path: '/search', query: next })
}

watch(() => route.fullPath, load, { immediate: true })
app.loadCategories()
priceMin.value = (route.query.minPrice as string) ?? ''
priceMax.value = (route.query.maxPrice as string) ?? ''

const SORTS: Array<{ key: NonNullable<SearchQuery['sort']>; text: string }> = [
  { key: 'default', text: '综合' },
  { key: 'sales', text: '销量' },
  { key: 'new', text: '新品' },
  { key: 'price_asc', text: '价格↑' },
  { key: 'price_desc', text: '价格↓' },
]
</script>

<template>
  <div class="container page">
    <div class="breadcrumb">
      <RouterLink to="/">首页</RouterLink>
      <Icon name="chevron-right" :size="12" />
      <a @click="patch({ categoryId: undefined })">全部商品</a>
      <template v-for="c in path" :key="c.id">
        <Icon name="chevron-right" :size="12" />
        <a @click="patch({ categoryId: c.id })">{{ c.name }}</a>
      </template>
      <template v-if="q.keyword">
        <Icon name="chevron-right" :size="12" />
        <span>“<b style="color: var(--c-primary)">{{ q.keyword }}</b>”</span>
      </template>
      <span class="muted" style="margin-left: auto">共 {{ total }} 件商品</span>
    </div>

    <div class="filters card">
      <dl>
        <dt>分类</dt>
        <dd>
          <a v-for="c in subCats" :key="c.id" :class="{ on: c.id === q.categoryId }" @click="patch({ categoryId: c.id })">{{ c.name }}</a>
        </dd>
      </dl>
      <dl v-if="brands.length">
        <dt>品牌</dt>
        <dd>
          <a :class="{ on: !q.brandId }" @click="patch({ brandId: undefined })">不限</a>
          <a v-for="b in brands" :key="b.id" :class="{ on: b.id === q.brandId }" @click="patch({ brandId: b.id })">{{ b.name }}</a>
        </dd>
      </dl>
      <dl>
        <dt>价格</dt>
        <dd>
          <a v-for="r in [[0, 100], [100, 500], [500, 2000], [2000, 8000], [8000, undefined]]" :key="String(r)"
             :class="{ on: q.minPrice === r[0] && q.maxPrice === r[1] }"
             @click="patch({ minPrice: r[0], maxPrice: r[1] })">{{ r[1] ? `${r[0]}-${r[1]}` : `${r[0]} 以上` }}</a>
          <span class="range">
            <input v-model="priceMin" class="input" placeholder="¥" />—<input v-model="priceMax" class="input" placeholder="¥" />
            <button class="btn btn-sm" @click="patch({ minPrice: priceMin || undefined, maxPrice: priceMax || undefined })">确定</button>
          </span>
        </dd>
      </dl>
    </div>

    <div class="sortbar card">
      <a v-for="s in SORTS" :key="s.key" :class="{ on: q.sort === s.key }" @click="patch({ sort: s.key })">{{ s.text }}</a>
      <span class="hint muted"><Icon name="zap" :size="14" /> 搜索服务：后端建议 MySQL 全文索引起步，后续可切 Elasticsearch</span>
    </div>

    <div v-if="loading && !list.length" class="grid">
      <div v-for="i in 10" :key="i" class="skeleton" style="height: 280px" />
    </div>
    <div v-else-if="list.length" class="grid" :style="{ opacity: loading ? 0.6 : 1 }">
      <ProductCard v-for="p in list" :key="p.id" :item="p" />
    </div>
    <Empty v-else icon="search" text="没有找到相关商品，换个关键词试试">
      <RouterLink to="/search" class="btn btn-ghost">查看全部商品</RouterLink>
    </Empty>
    <Pagination :page="q.page ?? 1" :size="q.size ?? 20" :total="total" @change="(p) => patch({ page: p })" />
  </div>
</template>

<style scoped>
.filters {
  padding: 4px 20px;
}
dl {
  display: flex;
  gap: 20px;
  padding: 12px 0;
  margin: 0;
  border-bottom: 1px dashed var(--c-border);
}
dl:last-child {
  border: none;
}
dt {
  width: 48px;
  color: var(--c-text-3);
  flex: none;
}
dd {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  align-items: center;
}
dd a {
  cursor: pointer;
}
dd a:hover,
dd a.on {
  color: var(--c-primary);
}
dd a.on {
  font-weight: 600;
}
.range {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--c-text-3);
}
.range .input {
  width: 70px;
  height: 28px;
}
.sortbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  margin: 12px 0;
}
.sortbar a {
  padding: 4px 16px;
  border-radius: 999px;
  cursor: pointer;
}
.sortbar a.on {
  background: var(--c-primary-soft);
  color: var(--c-primary);
  font-weight: 600;
}
.hint {
  margin-left: auto;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
}
</style>
