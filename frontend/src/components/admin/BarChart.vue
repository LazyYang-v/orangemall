<script setup lang="ts">
/**
 * 单序列柱状图（内联 SVG，无第三方依赖）
 * - 一个图只画一个度量（不做双轴）；颜色单一色相，表示量级
 * - 柱顶 4px 圆角、底部贴基线；网格与坐标轴弱化
 * - 每根柱子整列可悬停，显示 tooltip；只直标最大值
 * - 提供表格视图（可访问性）
 */
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{ data: Array<{ label: string; value: number }>; format?: (v: number) => string; height?: number; unit?: string }>(),
  { format: (v: number) => String(v), height: 220, unit: '' },
)
const W = 560
const PAD = { l: 48, r: 12, t: 22, b: 26 }
const hover = ref(-1)
const asTable = ref(false)

function niceMax(v: number) {
  if (v <= 0) return 1
  const p = 10 ** Math.floor(Math.log10(v))
  const n = v / p
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p
}
// 整数度量（如订单数）至少取 2 作为上限，保证 0 / 中线 / 顶线 三条刻度都是整数
const max = computed(() => {
  const m = niceMax(Math.max(...props.data.map((d) => d.value), 0))
  return props.data.every((d) => Number.isInteger(d.value)) ? Math.max(2, m) : m
})
const innerH = computed(() => props.height - PAD.t - PAD.b)
const step = computed(() => (W - PAD.l - PAD.r) / Math.max(props.data.length, 1))
const barW = computed(() => Math.min(36, step.value * 0.5))
const ticks = computed(() => [0, 0.5, 1].map((r) => ({ v: max.value * r, y: PAD.t + innerH.value * (1 - r) })))
const peak = computed(() => props.data.reduce((bi, d, i, a) => (d.value > a[bi].value ? i : bi), 0))
const bars = computed(() =>
  props.data.map((d, i) => {
    const h = max.value ? (d.value / max.value) * innerH.value : 0
    const x = PAD.l + step.value * i + (step.value - barW.value) / 2
    const y = PAD.t + innerH.value - h
    const r = Math.min(4, h, barW.value / 2)
    const base = PAD.t + innerH.value
    const path = h <= 0 ? '' : `M${x},${base} V${y + r} Q${x},${y} ${x + r},${y} H${x + barW.value - r} Q${x + barW.value},${y} ${x + barW.value},${y + r} V${base} Z`
    return { ...d, x, y, h, path, cx: x + barW.value / 2, colX: PAD.l + step.value * i }
  }),
)
const compact = (v: number) => (v >= 10000 ? `${+(v / 10000).toFixed(1)}万` : String(+v.toFixed(1)))
</script>

<template>
  <div class="bc">
    <button class="toggle" @click="asTable = !asTable">{{ asTable ? '图表' : '表格' }}</button>
    <table v-if="asTable" class="table">
      <thead><tr><th>日期</th><th>数值</th></tr></thead>
      <tbody><tr v-for="d in data" :key="d.label"><td>{{ d.label }}</td><td class="num">{{ format(d.value) }}{{ unit }}</td></tr></tbody>
    </table>
    <div v-else class="plot">
      <svg :viewBox="`0 0 ${W} ${height}`" role="img" @mouseleave="hover = -1">
        <g v-for="t in ticks" :key="t.v">
          <line :x1="PAD.l" :x2="W - PAD.r" :y1="t.y" :y2="t.y" class="grid" :class="{ base: t.v === 0 }" />
          <text :x="PAD.l - 8" :y="t.y + 4" text-anchor="end" class="ax">{{ compact(t.v) }}</text>
        </g>
        <g v-for="(b, i) in bars" :key="b.label">
          <rect :x="b.colX" :y="PAD.t" :width="step" :height="innerH" class="hit" :class="{ on: hover === i }" @mouseenter="hover = i" />
          <path v-if="b.path" :d="b.path" class="bar" :class="{ dim: hover >= 0 && hover !== i }" />
          <text :x="b.cx" :y="height - 8" text-anchor="middle" class="ax">{{ b.label }}</text>
          <text v-if="i === peak && b.value > 0 && hover < 0" :x="b.cx" :y="b.y - 6" text-anchor="middle" class="lbl">{{ format(b.value) }}</text>
        </g>
      </svg>
      <div v-if="hover >= 0" class="tip" :style="{ left: `${(bars[hover].cx / W) * 100}%`, top: `${(bars[hover].y / height) * 100}%` }">
        <span>{{ bars[hover].label }}</span><b class="num">{{ format(bars[hover].value) }}{{ unit }}</b>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bc {
  position: relative;
}
.toggle {
  position: absolute;
  top: -34px;
  right: 0;
  font-size: 12px;
  border: 1px solid var(--c-border-2);
  background: #fff;
  border-radius: 4px;
  padding: 1px 8px;
  color: var(--c-text-2);
}
.plot {
  position: relative;
}
svg {
  width: 100%;
  display: block;
}
.grid {
  stroke: #eef0f3;
  stroke-width: 1;
}
.grid.base {
  stroke: #d5d8de;
}
.ax {
  font-size: 11px;
  fill: #8c8c8c;
}
.lbl {
  font-size: 11px;
  fill: #1f1f1f;
  font-weight: 600;
}
.hit {
  fill: transparent;
}
.hit.on {
  fill: rgba(0, 0, 0, 0.03);
}
.bar {
  fill: #ff6a1a;
  pointer-events: none;
  transition: opacity 0.15s;
}
.bar.dim {
  opacity: 0.45;
}
.tip {
  position: absolute;
  transform: translate(-50%, calc(-100% - 8px));
  background: #1f1f1f;
  color: #fff;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  display: flex;
  gap: 8px;
}
</style>
