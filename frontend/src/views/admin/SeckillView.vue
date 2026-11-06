<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { adminApi, type AdminSeckillItem, type SeckillMetrics } from '@/api/admin'
import type { SeckillSession } from '@/types/domain'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { yuan, formatTime } from '@/utils/format'
import { SECKILL_STATUS } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import SeckillReliability from '@/components/admin/SeckillReliability.vue'

type Sess = SeckillSession & { items: AdminSeckillItem[] }
const sessions = ref<Sess[]>([])
const metrics = ref<SeckillMetrics | null>(null)
const users = ref(1000)
const busy = ref('')
const lastSim = ref<{ name: string; requests: number; success: number; soldOut: number; limited: number; redisLeft: number; dbLeft: number } | null>(null)

async function load() {
  const r = await adminApi.seckill()
  sessions.value = r.sessions
  metrics.value = r.metrics
}
let timer = 0
onMounted(() => {
  load()
  timer = window.setInterval(load, 3000)
})
onUnmounted(() => clearInterval(timer))

async function warm(i: AdminSeckillItem) {
  busy.value = i.activityId
  try {
    await adminApi.warmUp(i.activityId)
    toast.success(`已预热 seckill:stock:${i.activityId} = ${i.totalStock}`)
    await load()
  } finally {
    busy.value = ''
  }
}
async function simulate(i: AdminSeckillItem) {
  busy.value = i.activityId
  try {
    const r = await adminApi.simulate(i.activityId, users.value)
    lastSim.value = { name: i.name, ...r }
    await load()
  } catch (e) {
    toast.error((e as ApiError).message)
  } finally {
    busy.value = ''
  }
}

/** 四层防护拦截漏斗：请求 → 各层拦截 → 进入 MQ → 落库 */
const funnel = computed(() => {
  const m = metrics.value
  if (!m) return []
  return [
    { k: '总请求', v: m.totalRequests, d: '网关入口' },
    { k: '① 令牌桶限流', v: m.rateLimited, d: 'Sentinel / 令牌桶，按用户', block: true },
    { k: '② 非法路径', v: m.illegalPath, d: '动态 path 校验失败（脚本直刷）', block: true },
    { k: '③ 重复购买', v: m.repeatBlocked, d: 'Lua：SISMEMBER 一人一单', block: true },
    { k: '④ 库存不足', v: m.soldOut, d: 'Lua：DECR < 0 直接返回', block: true },
    { k: '进入 MQ', v: m.enqueued, d: 'Kafka 削峰，异步下单' },
    { k: '订单落库', v: m.ordersCreated, d: 'UPDATE ... WHERE stock > 0（乐观锁）' },
  ]
})
const fMax = computed(() => Math.max(1, ...funnel.value.map((f) => f.v)))
const oversold = (i: AdminSeckillItem) => i.redisStock !== null && i.redisStock < 0
</script>

<template>
  <div class="adm-page">
    <div class="adm-title">
      <h2>秒杀活动</h2><span class="sub">每 2 小时一场；开抢前需预热 Redis 库存；3 秒自动刷新</span>
      <div class="right">
        <label class="muted" style="font-size: 13px">模拟并发用户</label>
        <select v-model.number="users"><option :value="200">200</option><option :value="1000">1000</option><option :value="5000">5000</option></select>
      </div>
    </div>
    <div class="top">
      <div class="adm-card">
        <h3>四层防护拦截漏斗 <span class="hint">累计，含真实用户抢购与模拟请求</span></h3>
        <div v-for="f in funnel" :key="f.k" class="fr" :title="f.d">
          <span class="fk">{{ f.k }}</span>
          <span class="track"><i :class="{ blk: f.block }" :style="{ width: (f.v / fMax) * 100 + '%' }" /></span>
          <b class="num">{{ f.v.toLocaleString() }}</b>
          <span class="fd">{{ f.d }}</span>
        </div>
      </div>
      <div class="adm-card sim">
        <h3>最近一次模拟</h3>
        <template v-if="lastSim">
          <p class="ellipsis">{{ lastSim.name }}</p>
          <div class="kv">
            <div class="kpi"><span class="k">请求</span><span class="v">{{ lastSim.requests }}</span></div>
            <div class="kpi"><span class="k">抢到</span><span class="v ok">{{ lastSim.success }}</span></div>
            <div class="kpi"><span class="k">售罄</span><span class="v">{{ lastSim.soldOut }}</span></div>
            <div class="kpi"><span class="k">限流</span><span class="v">{{ lastSim.limited }}</span></div>
          </div>
          <p class="verdict" :class="{ bad: lastSim.redisLeft < 0 || lastSim.redisLeft !== lastSim.dbLeft }">
            Redis 剩余 <b class="num">{{ lastSim.redisLeft }}</b> · DB 剩余 <b class="num">{{ lastSim.dbLeft }}</b> ·
            {{ lastSim.redisLeft >= 0 && lastSim.redisLeft === lastSim.dbLeft ? '无超卖，缓存与 DB 一致 ✓' : '存在不一致，去「缓存一致性」修复' }}
          </p>
        </template>
        <p v-else class="muted">选择一个「抢购中」的活动，点击「模拟抢购」，用 N 个并发请求验证不超卖。</p>
      </div>
    </div>

    <SeckillReliability />

    <div v-for="s in sessions" :key="s.id" class="adm-card">
      <h3>
        {{ s.label }} 场 <span class="tag" :class="s.status === 'ONGOING' ? 'tag-red' : s.status === 'UPCOMING' ? 'tag-blue' : 'tag-gray'">{{ SECKILL_STATUS[s.status] }}</span>
        <span class="hint">{{ formatTime(s.startAt) }} ~ {{ formatTime(s.endAt) }}</span>
      </h3>
      <table class="table">
        <thead><tr><th>活动 ID</th><th>商品</th><th>秒杀价</th><th>总库存</th><th>Redis 库存</th><th>DB 库存</th><th>每人限购</th><th>预热</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="i in s.items" :key="i.activityId">
            <td class="mono">{{ i.activityId }}</td>
            <td><div class="goods"><ProductArt :kind="i.art" :tint="i.tint" :size="36" bg /><span class="ellipsis">{{ i.name }}</span></div></td>
            <td class="price">¥{{ yuan(i.seckillPrice) }}</td>
            <td class="num">{{ i.totalStock }}</td>
            <td class="num" :class="{ bad: oversold(i) }">{{ i.redisStock ?? '—' }}</td>
            <td class="num" :class="{ bad: i.redisStock !== null && i.dbStock !== i.redisStock }">{{ i.dbStock ?? '—' }}</td>
            <td class="num">{{ i.limitPerUser }}</td>
            <td><span class="tag" :class="i.warmed ? 'tag-green' : 'tag-gray'">{{ i.warmed ? '已预热' : '未预热' }}</span></td>
            <td>
              <button class="op" :disabled="busy === i.activityId || s.status === 'ENDED'" @click="warm(i)">{{ i.warmed ? '重新预热' : '预热' }}</button>
              <button class="op" :disabled="busy === i.activityId || s.status !== 'ONGOING'" @click="simulate(i)">模拟抢购</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.top {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.top .adm-card + .adm-card {
  margin-top: 0;
}
.fr {
  display: grid;
  grid-template-columns: 110px 1fr 64px 220px;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  padding: 5px 0;
}
.fk {
  color: var(--c-text-2);
}
.track {
  height: 12px;
  background: #f3f4f6;
  border-radius: 0 4px 4px 0;
}
.track i {
  display: block;
  height: 100%;
  background: #ff6a1a;
  border-radius: 0 4px 4px 0;
}
.track i.blk {
  background: #9aa0ab;
}
.fr b {
  text-align: right;
}
.fd {
  font-size: 12px;
  color: var(--c-text-3);
}
.sim p {
  font-size: 13px;
}
.kv {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 14px 0;
}
.kv .v {
  font-size: 22px;
}
.kv .v.ok {
  color: var(--c-success);
}
.verdict {
  background: #f0fbf4;
  color: var(--c-success);
  border-radius: 6px;
  padding: 8px 10px;
}
.verdict.bad {
  background: #fff1f1;
  color: var(--c-red);
}
.goods {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 260px;
}
.bad {
  color: var(--c-red);
  font-weight: 700;
}
</style>
