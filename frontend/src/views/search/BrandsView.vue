<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import ProductArt from '@/components/common/ProductArt.vue'
import { yuan } from '@/utils/format'

interface BrandBlock { id: number; name: string; shop: string; items: SpuCard[] }
const brands = ref<BrandBlock[]>([])
onMounted(async () => {
  const r = await productApi.search({ size: 100 })
  const map = new Map<number, BrandBlock>()
  r.records.forEach((s) => {
    const b = map.get(s.brandId) ?? { id: s.brandId, name: s.brandName, shop: s.shopName, items: [] }
    b.items.push(s)
    map.set(s.brandId, b)
  })
  brands.value = [...map.values()].sort((a, b) => b.items.length - a.items.length)
})
</script>

<template>
  <div class="container page">
    <h2 class="section-title" style="margin: 8px 0 16px">品牌馆 <small class="muted" style="font-size: 13px; font-weight: 400">官方旗舰 · 正品直供</small></h2>
    <div class="grid">
      <div v-for="b in brands" :key="b.id" class="card brand">
        <div class="bh">
          <span class="logo">{{ b.name.slice(0, 1) }}</span>
          <div>
            <b>{{ b.name }}</b>
            <p class="muted">{{ b.shop }} · {{ b.items.length }} 件在售</p>
          </div>
          <RouterLink :to="{ path: '/search', query: { brandId: b.id } }" class="btn btn-ghost btn-sm">进店</RouterLink>
        </div>
        <div class="items">
          <RouterLink v-for="s in b.items.slice(0, 3)" :key="s.id" :to="`/product/${s.id}`">
            <ProductArt :kind="s.art" :tint="s.tint" bg />
            <span class="price">¥{{ yuan(s.minPrice) }}</span>
          </RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.brand {
  padding: 16px;
}
.bh {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bh > div {
  flex: 1;
}
.bh p {
  font-size: 12px;
}
.logo {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: var(--g-primary);
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}
.items {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 14px;
}
.items a {
  text-align: center;
}
.items .price {
  font-size: 14px;
}
@media (max-width: 900px) {
  .grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
