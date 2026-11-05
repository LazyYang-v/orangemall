<script setup lang="ts">
import type { SpuCard } from '@/types/domain'
import ProductArt from '../common/ProductArt.vue'
import Icon from '../common/Icon.vue'
import { yuan } from '@/utils/format'
defineProps<{ item?: SpuCard }>()
</script>

<template>
  <section class="panel">
    <header>
      <h3>新品首发</h3>
      <span class="hot">HOT</span>
      <RouterLink to="/search?sort=new" class="more">更多 <Icon name="chevron-right" :size="12" /></RouterLink>
    </header>
    <RouterLink v-if="item" :to="`/product/${item.id}`" class="body">
      <ProductArt :kind="item.art" :tint="item.tint" :size="128" class="pic" />
      <div class="info">
        <b class="ellipsis-2">{{ item.name.replace(/ 全画幅相机$/, '') }}</b>
        <p class="ellipsis">{{ item.subtitle.split(' · ').slice(0, 2).join(' · ') }}</p>
        <span class="price"><small>¥</small>{{ Number(yuan(item.minPrice)).toLocaleString() }}</span>
      </div>
      <span class="go"><Icon name="chevron-right" :size="14" :stroke="2.4" /></span>
    </RouterLink>
    <div v-else class="skeleton" style="height: 120px" />
  </section>
</template>

<style scoped>
.panel {
  background: linear-gradient(160deg, #fff 0%, #fff6f0 100%);
  border-radius: var(--radius-lg);
  padding: 14px 18px 16px;
}
header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
h3 {
  font-size: 20px;
  font-weight: 800;
}
.hot {
  background: linear-gradient(90deg, #ff3a3a, #ff6a2a);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  padding: 1px 6px;
  border-radius: 4px;
  font-style: italic;
}
.more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  color: var(--c-text-2);
}
.body {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}
.pic {
  flex: none;
  transition: transform 0.25s;
}
.body:hover .pic {
  transform: rotate(-4deg) scale(1.05);
}
.info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.info b {
  font-size: 16px;
}
.info p {
  font-size: 12px;
  color: var(--c-text-3);
}
.price {
  font-size: 20px;
  margin-top: 6px;
}
.go {
  position: absolute;
  right: 0;
  bottom: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffe7da;
  color: var(--c-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
