<script setup lang="ts">
import { computed } from 'vue'
import { yuan } from '@/utils/format'
const props = withDefaults(defineProps<{ value: number; size?: number; del?: number; fixed?: boolean }>(), { size: 18, fixed: false })
const parts = computed(() => {
  const s = yuan(props.value, props.fixed)
  const [i, d] = s.split('.')
  return { i, d }
})
</script>

<template>
  <span class="price-wrap">
    <span class="price" :style="{ fontSize: size + 'px' }"><small>¥</small>{{ parts.i }}<small v-if="parts.d">.{{ parts.d }}</small></span>
    <span v-if="del && del > value" class="price-del">¥{{ yuan(del) }}</span>
  </span>
</template>

<style scoped>
.price-wrap {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
</style>
