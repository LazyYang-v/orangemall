<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ change: [number] }>()
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const list = computed(() => {
  const out: number[] = []
  const start = Math.max(1, Math.min(props.page - 2, pages.value - 4))
  for (let i = start; i <= Math.min(pages.value, start + 4); i++) out.push(i)
  return out
})
</script>

<template>
  <div v-if="pages > 1" class="pager">
    <button :disabled="page <= 1" @click="emit('change', page - 1)">上一页</button>
    <button v-for="p in list" :key="p" :class="{ on: p === page }" @click="emit('change', p)">{{ p }}</button>
    <button :disabled="page >= pages" @click="emit('change', page + 1)">下一页</button>
    <span class="muted">共 {{ total }} 条</span>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 0;
}
button {
  min-width: 34px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--c-border-2);
  border-radius: 6px;
  background: #fff;
}
button:hover:not(:disabled) {
  color: var(--c-primary);
  border-color: var(--c-primary);
}
button.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
button:disabled {
  color: #ccc;
  cursor: not-allowed;
}
.muted {
  font-size: 12px;
  margin-left: 6px;
}
</style>
