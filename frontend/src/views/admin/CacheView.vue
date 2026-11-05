<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { CacheCheckRow } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { toast } from '@/utils/toast'

const rows = ref<CacheCheckRow[]>([])
const stat = ref({ hitRate: 0, hit: 0, miss: 0 })
async function load() {
  const r = await adminApi.cacheCheck()
  rows.value = r.rows
  stat.value = { hitRate: r.hitRate, hit: r.hit, miss: r.miss }
}
onMounted(load)
async function repair(r: CacheCheckRow) {
  await adminApi.cacheRepair(r.key)
  toast.success(`${r.key} 已以 DB 为准修复`)
  load()
}
async function repairAll() {
  const bad = rows.value.filter((r) => !r.consistent)
  for (const r of bad) await adminApi.cacheRepair(r.key)
  toast.success(`已修复 ${bad.length} 个 key`)
  load()
}
const badCount = () => rows.value.filter((r) => !r.consistent).length
</script>

<template>
  <div class="adm-page">
    <div class="adm-title">
      <h2>缓存一致性</h2><span class="sub">定时任务比对 Redis 预扣库存与 DB 实际库存，不一致时以 DB 为准回写</span>
      <div class="right"><button class="btn btn-sm btn-square" @click="load">重新校验</button></div>
    </div>
    <div class="adm-grid kpis">
      <div class="adm-card kpi"><span class="k">商品详情缓存命中率</span><span class="v">{{ (stat.hitRate * 100).toFixed(1) }}%</span><span class="d">hit {{ stat.hit }} / miss {{ stat.miss }}</span></div>
      <div class="adm-card kpi"><span class="k">已校验 key</span><span class="v">{{ rows.length }}</span><span class="d">seckill:stock:*（最近 12 个）</span></div>
      <div class="adm-card kpi"><span class="k">不一致</span><span class="v" :class="{ bad: badCount() }">{{ badCount() }}</span><span class="d">{{ badCount() ? '建议立即修复' : '全部一致' }}</span></div>
    </div>
    <div class="adm-card">
      <h3>校验结果 <button v-if="badCount()" class="op" style="margin-left: auto" @click="repairAll">一键修复全部</button></h3>
      <table class="table">
        <thead><tr><th>Key</th><th>Redis 值</th><th>DB 值</th><th>差值</th><th>结果</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="r in rows" :key="r.key">
            <td class="mono">{{ r.key }}</td>
            <td class="num">{{ r.redisValue ?? 'nil' }}</td>
            <td class="num">{{ r.dbValue }}</td>
            <td class="num" :class="{ bad: !r.consistent }">{{ (r.redisValue ?? 0) - r.dbValue }}</td>
            <td><span class="tag" :class="r.consistent ? 'tag-green' : 'tag-red'">{{ r.consistent ? '✓ 一致' : '✕ 不一致' }}</span></td>
            <td><button class="op" :disabled="r.consistent" @click="repair(r)">以 DB 为准修复</button></td>
          </tr>
          <tr v-if="!rows.length"><td colspan="6" class="muted" style="text-align: center; padding: 30px">暂无预热的秒杀库存，去「秒杀活动」预热后再校验</td></tr>
        </tbody>
      </table>
    </div>
    <div class="adm-card notes">
      <h3>一致性策略说明</h3>
      <ul>
        <li><b>商品详情：</b>Cache Aside —— 读：先缓存后 DB 并回填；写：先更新 DB 再删缓存，500ms 后再删一次（延迟双删），防止并发读把旧值写回。</li>
        <li><b>秒杀库存：</b>Redis Lua 原子预扣，MQ 消费成功后 DB 乐观锁扣减；消费失败进入重试/死信，由本页的定时校验兜底。</li>
        <li><b>穿透 / 击穿 / 雪崩：</b>空值缓存 + 布隆过滤器；热点 key 互斥锁重建；过期时间加随机抖动。</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  grid-template-columns: repeat(3, 1fr);
  margin-bottom: 16px;
}
.bad {
  color: var(--c-red);
  font-weight: 700;
}
.notes ul {
  font-size: 13px;
  color: var(--c-text-2);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
