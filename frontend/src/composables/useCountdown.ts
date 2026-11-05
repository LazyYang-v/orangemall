import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import { splitDuration } from '@/utils/format'

/** 倒计时（以服务端时间偏移校准，避免本地时钟不准导致提前开抢） */
export function useCountdown(target: Ref<string | number | undefined>, offset = 0) {
  const now = ref(Date.now() + offset)
  const timer = setInterval(() => (now.value = Date.now() + offset), 250)
  onUnmounted(() => clearInterval(timer))
  const remain = computed(() => (target.value ? Math.max(0, new Date(target.value).getTime() - now.value) : 0))
  const parts = computed(() => splitDuration(remain.value))
  const done = ref(false)
  watch(remain, (v) => (done.value = v <= 0), { immediate: true })
  return { remain, parts, done, now }
}
