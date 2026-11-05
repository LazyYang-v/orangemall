<script setup lang="ts">
import type { SeckillItem } from '@/types/domain'
import ProductArt from '../common/ProductArt.vue'
import CountdownBox from '../common/CountdownBox.vue'
import Icon from '../common/Icon.vue'
import { percent, yuan } from '@/utils/format'

defineProps<{ endAt?: string; items: SeckillItem[] }>()
const emit = defineEmits<{ end: [] }>()
</script>

<template>
  <section class="panel">
    <header>
      <h3><Icon name="bolt" :size="22" />限时秒杀</h3>
      <span class="end">距结束</span>
      <CountdownBox :target="endAt" theme="red" @end="emit('end')" />
      <RouterLink to="/seckill" class="more">更多 <Icon name="chevron-right" :size="12" /></RouterLink>
    </header>
    <div class="list">
      <RouterLink v-for="it in items" :key="it.activityId" :to="`/seckill/${it.activityId}`" class="item">
        <ProductArt :kind="it.art" :tint="it.tint" :size="84" class="pic" />
        <div class="pr">
          <span class="price"><small>¥</small>{{ yuan(it.seckillPrice) }}</span>
          <span class="price-del">¥{{ yuan(it.originalPrice) }}</span>
        </div>
        <div class="bar">
          <i :style="{ width: percent(it.totalStock - it.remainStock, it.totalStock) + '%' }" />
          <span>{{ it.remainStock <= 0 ? '已抢光' : `已抢${percent(it.totalStock - it.remainStock, it.totalStock)}%` }}</span>
        </div>
      </RouterLink>
      <template v-if="!items.length">
        <div v-for="i in 4" :key="i" class="skeleton" style="height: 130px" />
      </template>
    </div>
  </section>
</template>

<style scoped>
.panel {
  background: linear-gradient(180deg, #fff1ea 0%, #fff 38%);
  border-radius: var(--radius-lg);
  padding: 14px 18px 16px;
}
header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
h3 {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 20px;
  font-weight: 800;
  color: var(--c-primary);
  margin-right: 20px;
}
.end {
  font-size: 14px;
}
.more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  color: var(--c-text-2);
}
.more:hover {
  color: var(--c-primary);
}
.list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pic {
  background: #fff;
  border-radius: 10px;
  transition: transform 0.2s;
}
.item:hover .pic {
  transform: scale(1.06);
}
.pr {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
}
.price {
  font-size: 17px;
}
.bar {
  position: relative;
  width: 100%;
  height: 14px;
  border-radius: 7px;
  background: #ffe1d6;
  overflow: hidden;
  margin-top: 4px;
}
.bar i {
  position: absolute;
  inset: 0 auto 0 0;
  background: linear-gradient(90deg, #ff6a3d, #ff2b2b);
  border-radius: 7px;
}
.bar span {
  position: relative;
  display: block;
  font-size: 10px;
  color: #fff;
  line-height: 14px;
  padding-left: 8px;
  font-weight: 600;
}
</style>
