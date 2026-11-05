<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { DashboardStats, OrderStatus } from '@/types/domain'
import { adminApi, type OpsLog } from '@/api/admin'
import { yuan, formatTime } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/dict'
import BarChart from '@/components/admin/BarChart.vue'

const pct = (r: number) => `${(r * 100).toFixed(1)}%`
const s = ref<DashboardStats | null>(null)
const logs = ref<OpsLog[]>([])
async function load() {
  const [d, l] = await Promise.all([adminApi.dashboard(), adminApi.logs()])
  s.value = d
  logs.value = l.slice(0, 8)
}
let timer = 0
onMounted(() => {
  load()
  timer = window.setInterval(load, 5000)
})
onUnmounted(() => clearInterval(timer))

const gmv = computed(() => s.value?.last7Days.map((d) => ({ label: d.date, value: d.gmv / 100 })) ?? [])
const orders = computed(() => s.value?.last7Days.map((d) => ({ label: d.date, value: d.orders })) ?? [])
const statusRows = computed(() => {
  if (!s.value) return []
  const entries = Object.entries(s.value.statusCount) as Array<[OrderStatus, number]>
  const total = entries.reduce((a, [, n]) => a + n, 0) || 1
  return entries.sort((a, b) => b[1] - a[1]).map(([k, n]) => ({ k, n, text: ORDER_STATUS[k].text, pct: n / total }))
})
const statusMax = computed(() => Math.max(1, ...statusRows.value.map((r) => r.n)))
const LOG_COLOR: Record<OpsLog['type'], string> = { CACHE: 'tag-blue', MQ: 'tag', PAY: 'tag-green', SECKILL: 'tag-red', ORDER: 'tag-gray', RISK: 'tag-red' }
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>数据看板</h2><span class="sub">每 5 秒自动刷新 · 金额为实付（已支付/已发货/已完成）</span></div>
    <template v-if="s">
      <div class="adm-grid kpis">
        <div class="adm-card kpi"><span class="k">今日 GMV</span><span class="v">¥{{ yuan(s.todayGmv, true) }}</span><span class="d">实付金额</span></div>
        <div class="adm-card kpi"><span class="k">今日订单</span><span class="v">{{ s.todayOrders }}</span><span class="d">含待支付</span></div>
        <div class="adm-card kpi"><span class="k">支付转化率</span><span class="v">{{ pct(s.conversionRate) }}</span><span class="d">今日已付 / 今日下单</span></div>
        <RouterLink to="/admin/orders?status=PAID" class="adm-card kpi warn"><span class="k">待发货</span><span class="v">{{ s.pendingShip }}</span><span class="d">去发货 →</span></RouterLink>
      </div>
      <div class="adm-grid charts">
        <div class="adm-card"><h3>近 7 日 GMV <span class="hint">元</span></h3><BarChart :data="gmv" :format="(v) => '¥' + v.toLocaleString('zh-CN', { maximumFractionDigits: 2 })" /></div>
        <div class="adm-card"><h3>近 7 日支付订单数 <span class="hint">单</span></h3><BarChart :data="orders" unit=" 单" /></div>
      </div>
      <div class="adm-grid charts">
        <div class="adm-card">
          <h3>订单状态分布 <span class="hint">全部订单</span></h3>
          <div v-for="r in statusRows" :key="r.k" class="srow" :title="`${r.text}：${r.n} 单（${pct(r.pct)}）`">
            <span class="sl">{{ r.text }}</span>
            <span class="track"><i :style="{ width: (r.n / statusMax) * 100 + '%' }" /></span>
            <b class="num">{{ r.n }}</b><span class="pc num">{{ pct(r.pct) }}</span>
          </div>
        </div>
        <div class="adm-card">
          <h3>最近链路事件 <RouterLink to="/admin/logs" class="hint more">全部 →</RouterLink></h3>
          <div v-for="(l, i) in logs" :key="i" class="lg">
            <span class="tag" :class="LOG_COLOR[l.type]">{{ l.type }}</span>
            <span class="t ellipsis" :title="l.text">{{ l.text }}</span>
            <span class="tm num">{{ formatTime(l.time, true).slice(11) }}</span>
          </div>
          <p v-if="!logs.length" class="muted">暂无事件，去商城下单 / 秒杀试试</p>
        </div>
      </div>
    </template>
    <div v-else class="skeleton" style="height: 400px" />
  </div>
</template>

<style scoped>
.kpis {
  grid-template-columns: repeat(4, 1fr);
}
.warn .v {
  color: var(--c-primary);
}
.charts {
  grid-template-columns: 1fr 1fr;
  margin-top: 16px;
}
.charts .adm-card + .adm-card {
  margin-top: 0;
}
.srow {
  display: grid;
  grid-template-columns: 56px 1fr 40px 52px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 7px 0;
}
.sl {
  color: var(--c-text-2);
}
.track {
  height: 10px;
  background: #f3f4f6;
  border-radius: 0 4px 4px 0;
}
.track i {
  display: block;
  height: 100%;
  background: #ff6a1a;
  border-radius: 0 4px 4px 0;
}
.srow b {
  text-align: right;
}
.pc {
  color: var(--c-text-3);
  font-size: 12px;
  text-align: right;
}
.more {
  margin-left: auto;
  color: var(--c-info);
}
.lg {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--c-border);
}
.lg .t {
  flex: 1;
  min-width: 0;
  color: var(--c-text-2);
}
.tm {
  color: var(--c-text-3);
}
</style>
