<script setup lang="ts">
/** 沙箱二维码示意：由内容哈希生成确定性点阵（真实环境用 qrcode 库渲染支付宝/微信返回的 code_url） */
import { computed } from 'vue'
const props = withDefaults(defineProps<{ text: string; size?: number; color?: string }>(), { size: 180, color: '#111' })
const N = 25
const cells = computed(() => {
  let h = 2166136261
  const out: Array<[number, number]> = []
  for (let i = 0; i < N * N; i++) {
    h ^= props.text.charCodeAt(i % props.text.length) + i
    h = Math.imul(h, 16777619) >>> 0
    const x = i % N
    const y = Math.floor(i / N)
    const finder = (x < 8 && y < 8) || (x >= N - 8 && y < 8) || (x < 8 && y >= N - 8)
    if (!finder && h % 100 < 47) out.push([x, y])
  }
  return out
})
const finders = [[0, 0], [N - 7, 0], [0, N - 7]]
</script>

<template>
  <svg :width="size" :height="size" :viewBox="`-1 -1 ${N + 2} ${N + 2}`" shape-rendering="crispEdges" aria-label="支付二维码">
    <rect x="-1" y="-1" :width="N + 2" :height="N + 2" fill="#fff" />
    <rect v-for="([x, y], i) in cells" :key="i" :x="x" :y="y" width="1" height="1" :fill="color" />
    <g v-for="([fx, fy], i) in finders" :key="'f' + i">
      <rect :x="fx" :y="fy" width="7" height="7" :fill="color" />
      <rect :x="fx + 1" :y="fy + 1" width="5" height="5" fill="#fff" />
      <rect :x="fx + 2" :y="fy + 2" width="3" height="3" :fill="color" />
    </g>
  </svg>
</template>
