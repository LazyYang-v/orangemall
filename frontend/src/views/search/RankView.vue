<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import { useAppStore } from '@/stores/app'
import ProductArt from '@/components/common/ProductArt.vue'
import { yuan } from '@/utils/format'

const app = useAppStore()
const cat = ref<number | undefined>(undefined)
const list = ref<SpuCard[]>([])
async function load() {
  list.value = (await productApi.search({ categoryId: cat.value, sort: 'sales', size: 10 })).records
}
onMounted(() => {
  app.loadCategories()
  load()
})
function pick(id?: number) {
  cat.value = id
  load()
}
</script>

<template>
  <div class="container page">
    <div class="head">
      <h2>热销排行榜</h2>
      <p>按实时销量排序 · 每小时更新（后端：Redis ZSET ZINCRBY 累加销量）</p>
    </div>
    <nav class="cats">
      <a :class="{ on: !cat }" @click="pick()">全部</a>
      <a v-for="c in app.categories" :key="c.id" :class="{ on: cat === c.id }" @click="pick(c.id)">{{ c.name }}</a>
    </nav>
    <div class="card list">
      <RouterLink v-for="(s, i) in list" :key="s.id" :to="`/product/${s.id}`" class="row">
        <span class="rank" :class="'r' + (i + 1)">{{ i + 1 }}</span>
        <ProductArt :kind="s.art" :tint="s.tint" :size="80" bg />
        <div class="nm">
          <b>{{ s.name }}</b>
          <p class="muted">{{ s.subtitle }}</p>
        </div>
        <span class="sales">已售 <b class="num">{{ s.sales.toLocaleString() }}</b></span>
        <span class="price">¥{{ yuan(s.minPrice) }}</span>
      </RouterLink>
      <p v-if="!list.length" class="muted" style="padding: 40px; text-align: center">该分类暂无商品</p>
    </div>
  </div>
</template>

<style scoped>
.head {
  background: linear-gradient(110deg, #ff8a1f, #ff4d00);
  color: #fff;
  border-radius: var(--radius-lg);
  padding: 24px 30px;
}
.head h2 {
  font-size: 30px;
  font-style: italic;
}
.cats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0;
}
.cats a {
  padding: 5px 14px;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}
.cats a.on {
  background: var(--c-primary);
  color: #fff;
}
.row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--c-border);
}
.row:hover {
  background: #fffaf6;
}
.rank {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #eee;
  color: #888;
  font-weight: 800;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-num);
}
.r1 { background: linear-gradient(135deg, #ffcf3d, #ff8a00); color: #fff; }
.r2 { background: linear-gradient(135deg, #d6dbe3, #9aa3b0); color: #fff; }
.r3 { background: linear-gradient(135deg, #f2b98a, #c8783e); color: #fff; }
.nm {
  flex: 1;
  min-width: 0;
}
.nm p {
  font-size: 12px;
  margin-top: 4px;
}
.sales {
  width: 140px;
  font-size: 13px;
  color: var(--c-text-2);
}
.price {
  width: 100px;
  text-align: right;
  font-size: 20px;
}
</style>
