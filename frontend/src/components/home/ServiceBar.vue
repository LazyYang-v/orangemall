<script setup lang="ts">
import Icon from '../common/Icon.vue'
const LIST = [
  { icon: 'shield', title: '正品保障', desc: '官方正品 · 假一赔十' },
  { icon: 'truck', title: '极速发货', desc: '全国多仓 · 次日达' },
  { icon: 'refresh', title: '无忧售后', desc: '7天无理由退换' },
  { icon: 'card', title: '分期免息', desc: '热门商品 · 0息购' },
]
</script>

<template>
  <div class="svc">
    <div v-for="s in LIST" :key="s.title" class="item">
      <Icon :name="s.icon" :size="30" :stroke="1.6" />
      <div>
        <b>{{ s.title }}</b>
        <p>{{ s.desc }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.svc {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  border-radius: var(--radius-lg);
  height: 56px;
  align-items: center;
}
.item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--c-primary);
  position: relative;
}
.item + .item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 1px;
  background: var(--c-border);
}
b {
  display: block;
  color: var(--c-text);
  font-size: 15px;
}
p {
  font-size: 12px;
  color: var(--c-text-3);
}
</style>
