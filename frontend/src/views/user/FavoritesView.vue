<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { SpuCard } from '@/types/domain'
import { productApi } from '@/api/product'
import ProductCard from '@/components/common/ProductCard.vue'
import Empty from '@/components/common/Empty.vue'
const list = ref<SpuCard[]>([])
const loaded = ref(false)
onMounted(async () => {
  list.value = await productApi.favorites()
  loaded.value = true
})
</script>

<template>
  <div class="card wrap">
    <h3>我的收藏（{{ list.length }}）</h3>
    <div v-if="list.length" class="grid"><ProductCard v-for="p in list" :key="p.id" :item="p" /></div>
    <Empty v-else-if="loaded" icon="heart" text="还没有收藏的商品"><RouterLink to="/" class="btn btn-primary">去逛逛</RouterLink></Empty>
  </div>
</template>

<style scoped>
.wrap {
  padding: 18px 22px;
}
h3 {
  font-size: 16px;
  margin-bottom: 14px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.grid :deep(.pcard) {
  border-color: var(--c-border);
}
@media (max-width: 900px) {
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
