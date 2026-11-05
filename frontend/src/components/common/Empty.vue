<script setup lang="ts">
import Icon from './Icon.vue'
withDefaults(defineProps<{ icon?: string; text?: string }>(), { icon: 'package', text: '暂无数据' })
</script>

<template>
  <div class="empty">
    <div class="circle"><Icon :name="icon" :size="40" :stroke="1.4" /></div>
    <p>{{ text }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 60px 0;
  color: var(--c-text-3);
}
.circle {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--c-primary-soft);
  color: var(--c-primary-2);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
