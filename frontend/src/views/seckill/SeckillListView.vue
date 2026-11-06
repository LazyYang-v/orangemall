<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { SeckillItem, SeckillSession } from '@/types/domain'
import { seckillApi } from '@/api/seckill'
import { percent, yuan } from '@/utils/format'
import { SECKILL_STATUS } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import CountdownBox from '@/components/common/CountdownBox.vue'
import Icon from '@/components/common/Icon.vue'

const sessions = ref<Array<SeckillSession & { items: SeckillItem[] }>>([])
const active = ref('')
async function load() {
  sessions.value = await seckillApi.sessions()
  if (!sessions.value.some((s) => s.id === active.value)) active.value = sessions.value.find((s) => s.status === 'ONGOING')?.id ?? sessions.value[0]?.id ?? ''
}
onMounted(load)
const cur = computed(() => sessions.value.find((s) => s.id === active.value))

const LAYERS = [
  { icon: 'shield', t: '网关限流', d: 'Nginx 令牌桶 + 动态秒杀地址' },
  { icon: 'zap', t: 'Redis 原子扣减', d: 'Lua：一人一单 + DECR 库存' },
  { icon: 'layers', t: 'MQ 异步削峰', d: '扣减成功即返回，异步落库' },
  { icon: 'database', t: '数据库兜底', d: '乐观锁 + Redisson 分布式锁' },
]
</script>

<template>
  <div class="seckill">
    <div class="banner">
      <div class="container">
        <h1><Icon name="bolt" :size="44" /> 限时秒杀</h1>
        <p>每 2 小时一场 · 每人每场限购 1 件 · 先到先得</p>
        <div class="layers">
          <div v-for="(l, i) in LAYERS" :key="l.t" class="layer">
            <span class="n">{{ i + 1 }}</span>
            <Icon :name="l.icon" :size="20" />
            <div><b>{{ l.t }}</b><small>{{ l.d }}</small></div>
          </div>
        </div>
      </div>
    </div>
    <div class="container">
      <nav class="sessions">
        <a v-for="s in sessions" :key="s.id" :class="{ on: s.id === active, [s.status]: true }" @click="active = s.id">
          <b class="num">{{ s.label }}</b>
          <span>{{ SECKILL_STATUS[s.status] }}</span>
        </a>
      </nav>
      <div v-if="cur" class="bar">
        <span v-if="cur.status === 'ONGOING'">本场距结束 <CountdownBox :target="cur.endAt" theme="dark" @end="load" /></span>
        <span v-else-if="cur.status === 'UPCOMING'">距开抢还有 <CountdownBox :target="cur.startAt" theme="dark" @end="load" />　<em>开抢前请勿提前刷新，秒杀地址开抢后才下发</em></span>
        <span v-else>本场已结束，看看下一场吧</span>
      </div>
      <div class="list">
        <RouterLink v-for="it in cur?.items ?? []" :key="it.activityId" :to="`/seckill/${it.activityId}`" class="card item">
          <ProductArt :kind="it.art" :tint="it.tint" :size="150" bg />
          <div class="info">
            <h3 class="ellipsis-2">{{ it.name }}</h3>
            <div class="pr">
              <span class="price"><small>¥</small>{{ yuan(it.seckillPrice) }}</span>
              <span class="price-del">¥{{ yuan(it.originalPrice) }}</span>
              <span class="tag tag-red">省 ¥{{ yuan(it.originalPrice - it.seckillPrice) }}</span>
            </div>
            <div class="prog">
              <div class="track"><i :style="{ width: percent(it.totalStock - it.remainStock, it.totalStock) + '%' }" /></div>
              <span>{{ it.status === 'UPCOMING' ? `限量 ${it.totalStock} 件` : `已抢 ${percent(it.totalStock - it.remainStock, it.totalStock)}%` }}</span>
            </div>
            <span class="go" :class="it.status === 'ONGOING' && it.remainStock > 0 ? 'hot' : it.status === 'UPCOMING' ? 'soon' : 'over'">
              {{ it.status === 'UPCOMING' ? '即将开抢' : it.status === 'ENDED' ? '已结束' : it.remainStock <= 0 ? '已抢光' : '马上抢' }}
            </span>
          </div>
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.banner {
  background: linear-gradient(115deg, #ff7a2d, #f2301d 55%, #c8102e);
  color: #fff;
  padding: 30px 0 24px;
}
h1 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 40px;
  font-style: italic;
  font-weight: 900;
}
.banner p {
  margin-top: 4px;
  opacity: 0.9;
}
.layers {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 20px;
}
@media (max-width: 900px) {
  .layers {
    grid-template-columns: repeat(2, 1fr);
  }
  .banner p {
    font-size: 12px;
  }
}
.layer {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: var(--radius);
  padding: 10px 14px;
}
.layer .n {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  color: var(--c-red);
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.layer b {
  display: block;
}
.layer small {
  opacity: 0.85;
}
.sessions {
  display: flex;
  background: #2b2b2b;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  overflow: hidden;
}
.sessions a {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
  color: #bbb;
  cursor: pointer;
}
.sessions b {
  font-size: 22px;
  color: #fff;
}
.sessions span {
  font-size: 12px;
}
.sessions a.on {
  background: var(--g-primary);
  color: #fff;
}
.sessions a.ENDED b {
  color: #888;
}
.bar {
  padding: 14px 4px;
  font-size: 14px;
}
.bar span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.bar em {
  font-style: normal;
  font-size: 12px;
  color: var(--c-text-3);
}
.list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.item {
  display: flex;
  gap: 18px;
  padding: 16px;
  transition: box-shadow 0.2s;
}
.item:hover {
  box-shadow: var(--shadow-lg);
}
.info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
  min-width: 0;
}
h3 {
  font-size: 16px;
  font-weight: 600;
}
.pr {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.pr .price {
  font-size: 28px;
}
.prog {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--c-red);
  width: 70%;
}
.track {
  flex: 1;
  height: 8px;
  background: #ffe1d6;
  border-radius: 4px;
  overflow: hidden;
}
.track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ff8a3d, #ff2b2b);
}
.go {
  position: absolute;
  right: 0;
  bottom: 0;
  height: 40px;
  padding: 0 26px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  font-weight: 700;
  font-size: 16px;
}
.go.hot {
  background: var(--g-primary);
  color: #fff;
}
.go.soon {
  background: #e8f7ee;
  color: var(--c-success);
}
.go.over {
  background: #eee;
  color: #999;
}
@media (max-width: 900px) {
  .list { grid-template-columns: minmax(0, 1fr); }
  .item { gap: 12px; padding: 12px; }
  .item > :first-child { flex: none; width: 96px; height: 96px; }
  .pr { flex-wrap: wrap; }
  .pr .price { font-size: 22px; }
  .prog { width: 100%; padding-right: 72px; }
  .sessions { overflow-x: auto; }
  .sessions a { flex: 0 0 auto; min-width: 76px; padding: 8px 10px; }
  .layers > * { min-width: 0; }
}
</style>
