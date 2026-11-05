<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { SeckillItem, SeckillResult } from '@/types/domain'
import { seckillApi } from '@/api/seckill'
import { useUserStore } from '@/stores/user'
import { percent, yuan, formatTime } from '@/utils/format'
import ProductArt from '@/components/common/ProductArt.vue'
import CountdownBox from '@/components/common/CountdownBox.vue'
import Icon from '@/components/common/Icon.vue'
import Modal from '@/components/common/Modal.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const activityId = route.params.activityId as string
const item = ref<(SeckillItem & { bought: boolean; serverTime: number }) | null>(null)
const phase = ref<'idle' | 'path' | 'submit' | 'queue' | 'done'>('idle')
const result = ref<SeckillResult | null>(null)
const trace = ref<Array<{ t: string; ok: boolean }>>([])
let pollTimer: ReturnType<typeof setTimeout> | undefined

async function load() {
  item.value = await seckillApi.item(activityId)
}
onMounted(load)
onUnmounted(() => clearTimeout(pollTimer))

const sold = computed(() => (item.value ? percent(item.value.totalStock - item.value.remainStock, item.value.totalStock) : 0))
const canBuy = computed(() => item.value?.status === 'ONGOING' && item.value.remainStock > 0 && !item.value.bought)

function step(t: string, ok = true) {
  trace.value.push({ t, ok })
}

/** 秒杀三步：① 获取动态地址 → ② 带地址提交（Redis Lua + MQ）→ ③ 轮询排队结果 */
async function grab() {
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  trace.value = []
  result.value = null
  try {
    phase.value = 'path'
    const { path } = await seckillApi.path(activityId)
    step(`① 获取动态秒杀地址 /seckill/${activityId.slice(-8)}/${path.slice(0, 8)}…（60s 有效）`)
    phase.value = 'submit'
    const r = await seckillApi.submit(activityId, path)
    step('② 网关令牌桶放行 → Lua 校验一人一单 + DECR 库存成功')
    step(`③ 消息已投递 Kafka，预生成订单号 ${r.orderId}，排队中…`)
    phase.value = 'queue'
    poll(0)
  } catch (e) {
    step((e as Error).message, false)
    result.value = { state: 'FAILED', reason: (e as Error).message }
    phase.value = 'done'
    load()
  }
}

function poll(n: number) {
  pollTimer = setTimeout(async () => {
    const r = await seckillApi.result(activityId)
    if (r.state === 'QUEUING' && n < 20) return poll(n + 1)
    result.value = r
    phase.value = 'done'
    if (r.state === 'SUCCESS') step('④ 消费者落库成功（乐观锁扣 DB 库存），ACK 消息')
    else step(r.reason ?? '抢购失败', false)
    load()
  }, 600)
}
</script>

<template>
  <div class="container page">
    <div class="breadcrumb">
      <RouterLink to="/seckill">限时秒杀</RouterLink><Icon name="chevron-right" :size="12" /><span>{{ item?.name }}</span>
    </div>
    <section v-if="item" class="card main">
      <div class="pic">
        <ProductArt :kind="item.art" :tint="item.tint" />
        <span class="flag"><Icon name="bolt" :size="14" /> 限时秒杀</span>
      </div>
      <div class="info">
        <h1>{{ item.name }}</h1>
        <div class="price-bar">
          <div>
            <span class="lbl">秒杀价</span>
            <span class="p num"><small>¥</small>{{ yuan(item.seckillPrice) }}</span>
            <s>¥{{ yuan(item.originalPrice) }}</s>
          </div>
          <div class="cd">
            <template v-if="item.status === 'ONGOING'">距结束<CountdownBox :target="item.endAt" theme="plain" @end="load" /></template>
            <template v-else-if="item.status === 'UPCOMING'">距开抢<CountdownBox :target="item.startAt" theme="plain" @end="load" /></template>
            <template v-else>已结束</template>
          </div>
        </div>
        <div class="prog">
          <div class="track"><i :style="{ width: sold + '%' }" /></div>
          <span>已抢 {{ sold }}%　剩余 <b class="num">{{ item.remainStock }}</b> / {{ item.totalStock }} 件</span>
        </div>
        <ul class="rules">
          <li><Icon name="clock" :size="14" /> 活动时间：{{ formatTime(item.startAt) }} ~ {{ formatTime(item.endAt) }}</li>
          <li><Icon name="user" :size="14" /> 每人限购 {{ item.limitPerUser }} 件，同账号/设备/地址视为同一用户</li>
          <li><Icon name="order" :size="14" /> 抢购成功后请在 10 分钟内付款，超时库存将释放</li>
          <li><Icon name="shield" :size="14" /> 异常高频请求会被信用分风控前置拦截</li>
        </ul>
        <button class="grab" :disabled="!canBuy || (phase !== 'idle' && phase !== 'done')" @click="grab">
          <template v-if="phase === 'path' || phase === 'submit'">抢购中…</template>
          <template v-else-if="phase === 'queue'">排队中，请勿刷新…</template>
          <template v-else-if="item.bought">您已抢到，去订单付款</template>
          <template v-else-if="item.status === 'UPCOMING'">即将开抢</template>
          <template v-else-if="item.status === 'ENDED'">活动已结束</template>
          <template v-else-if="item.remainStock <= 0">已抢光</template>
          <template v-else><Icon name="bolt" :size="22" /> 立即抢购</template>
        </button>
        <RouterLink v-if="item.bought" to="/orders?status=PENDING_PAYMENT" class="to-order">查看我的秒杀订单 <Icon name="chevron-right" :size="12" /></RouterLink>
      </div>
    </section>

    <section v-if="trace.length" class="card trace">
      <h3>本次请求链路</h3>
      <ol>
        <li v-for="(t, i) in trace" :key="i" :class="{ bad: !t.ok }">
          <Icon :name="t.ok ? 'check-circle' : 'alert'" :size="16" /> {{ t.t }}
        </li>
      </ol>
    </section>

    <Modal :open="phase === 'done' && !!result" :title="result?.state === 'SUCCESS' ? '恭喜，抢购成功！' : '很遗憾'" :width="420" @close="phase = 'idle'">
      <div class="res">
        <div class="res-ic" :class="result?.state"><Icon :name="result?.state === 'SUCCESS' ? 'check' : 'close'" :size="36" :stroke="3" /></div>
        <p v-if="result?.state === 'SUCCESS'">订单 <span class="num">{{ result.orderId }}</span> 已创建，请在 10 分钟内完成支付</p>
        <p v-else>{{ result?.reason }}</p>
      </div>
      <template #footer>
        <button class="btn" @click="phase = 'idle'">知道了</button>
        <RouterLink v-if="result?.state === 'SUCCESS'" :to="`/pay/${result.orderId}`" class="btn btn-primary">立即付款</RouterLink>
        <RouterLink v-else to="/seckill" class="btn btn-primary">看看其他秒杀</RouterLink>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.main {
  display: flex;
  gap: 36px;
  padding: 24px;
}
.pic {
  position: relative;
  width: 400px;
  flex: none;
  background: linear-gradient(160deg, #fff5ef, #ffe7da);
  border-radius: var(--radius-lg);
  padding: 30px;
}
.flag {
  position: absolute;
  left: 14px;
  top: 14px;
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--c-red);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
}
.info {
  flex: 1;
}
h1 {
  font-size: 22px;
}
.price-bar {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(90deg, #ff4d1a, #e3141f);
  color: #fff;
  border-radius: var(--radius);
  padding: 14px 20px;
}
.lbl {
  font-size: 13px;
  margin-right: 8px;
}
.p {
  font-size: 40px;
  font-weight: 800;
}
.p small {
  font-size: 20px;
}
s {
  margin-left: 10px;
  opacity: 0.7;
}
.cd {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.prog {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0;
  font-size: 13px;
  color: var(--c-red);
}
.track {
  width: 260px;
  height: 10px;
  background: #ffe1d6;
  border-radius: 5px;
  overflow: hidden;
}
.track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ff8a3d, #ff2b2b);
}
.rules {
  background: #fafafa;
  border-radius: var(--radius);
  padding: 12px 16px;
  font-size: 13px;
  color: var(--c-text-2);
  line-height: 2;
}
.rules li {
  display: flex;
  align-items: center;
  gap: 8px;
}
.grab {
  margin-top: 22px;
  width: 320px;
  height: 56px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff8a1f, #ff2b2b);
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 10px 24px rgba(255, 60, 20, 0.35);
}
.grab:disabled {
  background: #ddd;
  box-shadow: none;
  cursor: not-allowed;
}
.to-order {
  margin-left: 16px;
  color: var(--c-primary);
  font-size: 13px;
}
.trace {
  margin-top: 14px;
  padding: 18px 22px;
}
.trace h3 {
  font-size: 15px;
  margin-bottom: 10px;
}
.trace ol {
  padding: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2;
}
.trace li {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--c-success);
}
.trace li.bad {
  color: var(--c-red);
}
.res {
  text-align: center;
}
.res-ic {
  width: 70px;
  height: 70px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: #ccc;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.res-ic.SUCCESS {
  background: var(--c-success);
}
</style>
