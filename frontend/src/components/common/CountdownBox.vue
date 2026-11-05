<script setup lang="ts">
import { toRef, watch } from 'vue'
import { useCountdown } from '@/composables/useCountdown'
import { pad } from '@/utils/format'

const props = withDefaults(defineProps<{ target?: string; theme?: 'red' | 'dark' | 'plain'; showDays?: boolean }>(), { theme: 'red' })
const emit = defineEmits<{ end: [] }>()
const { parts, remain } = useCountdown(toRef(props, 'target'))
let fired = false
watch(() => props.target, () => (fired = false))
watch(remain, (v) => {
  if (v <= 0 && props.target && !fired) {
    fired = true
    emit('end')
  }
})
</script>

<template>
  <span class="cd" :class="theme">
    <template v-if="showDays && parts.d > 0"><b>{{ parts.d }}</b><i>天</i></template>
    <b>{{ pad(parts.h + (showDays ? 0 : parts.d * 24)) }}</b><i>:</i><b>{{ pad(parts.m) }}</b><i>:</i><b>{{ pad(parts.s) }}</b>
  </span>
</template>

<style scoped>
.cd {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-family: var(--font-num);
}
b {
  min-width: 22px;
  height: 22px;
  padding: 0 3px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}
i {
  font-style: normal;
  font-weight: 700;
}
.red b {
  background: #ff2b2b;
  color: #fff;
}
.red i {
  color: #ff2b2b;
}
.dark b {
  background: #222;
  color: #fff;
}
.plain b {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.plain i {
  color: #fff;
}
</style>
