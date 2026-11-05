<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import type { HomeData, SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import CategoryMenu from '@/components/layout/CategoryMenu.vue'
import HeroBanner from '@/components/home/HeroBanner.vue'
import ServiceBar from '@/components/home/ServiceBar.vue'
import UserPanel from '@/components/home/UserPanel.vue'
import SeckillPanel from '@/components/home/SeckillPanel.vue'
import CouponPanel from '@/components/home/CouponPanel.vue'
import NewArrivalPanel from '@/components/home/NewArrivalPanel.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import Icon from '@/components/common/Icon.vue'

const home = ref<HomeData | null>(null)
const recommend = ref<Array<SpuCard & { _k: string }>>([])
const page = ref(0)
const loadingMore = ref(false)
const MAX_PAGES = 4
const sentinel = ref<HTMLElement | null>(null)
let io: IntersectionObserver | undefined

async function loadHome() {
  home.value = await productApi.home()
}

async function loadMore() {
  if (loadingMore.value || page.value >= MAX_PAGES) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const r = await productApi.recommend(next, 16)
    recommend.value.push(...r.records.map((x) => ({ ...x, _k: `${next}-${x.id}` })))
    page.value = next
  } finally {
    loadingMore.value = false
  }
}

onMounted(() => {
  loadHome()
  loadMore()
  io = new IntersectionObserver((es) => es[0]?.isIntersecting && loadMore(), { rootMargin: '200px' })
  if (sentinel.value) io.observe(sentinel.value)
})
onUnmounted(() => io?.disconnect())
</script>

<template>
  <div class="container home">
    <section class="hero-row">
      <CategoryMenu />
      <div class="mid">
        <HeroBanner :banners="home?.banners ?? []" />
        <ServiceBar />
      </div>
      <UserPanel :notices="home?.notices ?? []" />
    </section>

    <section class="promo-row">
      <SeckillPanel :end-at="home?.seckillPreview.endAt" :items="home?.seckillPreview.items ?? []" @end="loadHome" />
      <CouponPanel :coupons="home?.couponPreview ?? []" @received="loadHome" />
      <NewArrivalPanel :item="home?.newArrival" />
    </section>

    <section class="guess">
      <h2 class="section-title">
        <Icon name="heart-fill" :size="24" style="color: var(--c-primary)" />
        <span class="t">猜你喜欢</span>
        <small>为你精心推荐</small>
      </h2>
      <div class="grid">
        <ProductCard v-for="p in recommend" :key="p._k" :item="p" />
        <template v-if="!recommend.length">
          <div v-for="i in 8" :key="i" class="skeleton" style="height: 240px" />
        </template>
      </div>
      <div ref="sentinel" class="more">
        <span v-if="loadingMore">正在加载更多好物…</span>
        <RouterLink v-else-if="page >= MAX_PAGES" to="/search" class="btn btn-ghost">去搜索页逛更多 <Icon name="chevron-right" :size="14" /></RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-top: 16px;
}
.hero-row {
  display: grid;
  grid-template-columns: 214px 1fr 292px;
  gap: 16px;
}
.mid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.promo-row {
  display: grid;
  grid-template-columns: 544fr 390fr 320fr;
  gap: 16px;
  margin-top: 16px;
}
.guess {
  margin-top: 24px;
}
.section-title .t {
  color: var(--c-primary);
  font-size: 22px;
  font-weight: 800;
}
.section-title small {
  font-size: 13px;
  font-weight: 400;
  color: var(--c-text-2);
}
.grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
}
.more {
  text-align: center;
  padding: 24px 0 0;
  color: var(--c-text-3);
  min-height: 40px;
}
</style>
