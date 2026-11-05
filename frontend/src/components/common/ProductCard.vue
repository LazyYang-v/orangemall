<script setup lang="ts">
import type { SpuCard } from '@/types/domain'
import ProductArt from './ProductArt.vue'
import Price from './Price.vue'

defineProps<{ item: SpuCard }>()

function salesText(n: number) {
  return n >= 10000 ? `${(n / 10000).toFixed(1)}万+` : `${n}`
}
</script>

<template>
  <RouterLink :to="`/product/${item.id}`" class="pcard">
    <div class="pic">
      <ProductArt :kind="item.art" :tint="item.tint" :src="item.imageUrl" />
      <span v-if="item.isNew" class="badge">NEW</span>
    </div>
    <div class="body">
      <h4 class="ellipsis-2">
        <span v-if="item.tags.includes('双11爆款')" class="tag tag-solid">双11</span>
        {{ item.name }}
      </h4>
      <div class="tags">
        <span v-for="t in item.tags.filter((x) => x !== '双11爆款').slice(0, 2)" :key="t" class="tag">{{ t }}</span>
      </div>
      <div class="foot">
        <Price :value="item.minPrice" :size="18" />
        <span class="sales">已售 {{ salesText(item.sales) }}</span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid transparent;
}
.pcard:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
  border-color: var(--c-primary-border);
}
.pic {
  position: relative;
  background: linear-gradient(170deg, #fbfbfc, #f2f2f4);
  padding: 10px;
}
.badge {
  position: absolute;
  left: 10px;
  top: 10px;
  background: var(--g-primary);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
}
.body {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
h4 {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
  height: 38px;
}
h4 .tag {
  vertical-align: 1px;
  margin-right: 2px;
}
.tags {
  display: flex;
  gap: 4px;
  height: 18px;
}
.foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.sales {
  font-size: 12px;
  color: var(--c-text-3);
}
</style>
