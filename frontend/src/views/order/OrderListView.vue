<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Order, OrderStatus } from '@/types/domain'
import { orderApi } from '@/api/order'
import { useCartStore } from '@/stores/cart'
import { useNotify } from '@/composables/useNotify'
import { toast } from '@/utils/toast'
import { yuan, formatTime } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import Pagination from '@/components/common/Pagination.vue'
import Empty from '@/components/common/Empty.vue'
import CountdownBox from '@/components/common/CountdownBox.vue'
import Icon from '@/components/common/Icon.vue'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const TABS: Array<{ key: OrderStatus | ''; text: string }> = [
  { key: '', text: '全部订单' },
  { key: 'PENDING_PAYMENT', text: '待付款' },
  { key: 'PAID', text: '待发货' },
  { key: 'SHIPPED', text: '待收货' },
  { key: 'COMPLETED', text: '已完成' },
  { key: 'CLOSED', text: '已取消/关闭' },
]
const status = ref<OrderStatus | ''>((route.query.status as OrderStatus) || '')
const keyword = ref('')
const page = ref(1)
const list = ref<Order[]>([])
const total = ref(0)
const counts = ref<Record<string, number>>({})
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [r, c] = await Promise.all([orderApi.list({ status: status.value, keyword: keyword.value, page: page.value, size: 5 }), orderApi.counts()])
    list.value = r.records
    total.value = r.total
    counts.value = c
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch([status, page], load)
watch(status, (s) => {
  page.value = 1
  router.replace({ query: s ? { status: s } : {} })
})

// WebSocket：订单状态 / 物流变化时刷新列表
useNotify((m) => {
  if (m.type === 'ORDER_STATUS') toast.info(`订单 ${m.orderId.slice(-6)}：${m.text}`)
  if (m.type === 'ORDER_STATUS' || m.type === 'LOGISTICS_UPDATE') load()
})

async function cancel(o: Order) {
  if (!confirm('确定取消该订单？库存、优惠券和积分将退回')) return
  await orderApi.cancel(o.id)
  toast.success('订单已取消')
  load()
}
async function confirmRecv(o: Order) {
  if (!confirm('确认已收到商品？')) return
  const r = await orderApi.confirm(o.id)
  toast.success(`确认收货成功，获得 ${r.earnedPoints} 积分`)
  load()
}
async function rebuy(o: Order) {
  try {
    for (const it of o.items) await cart.add(it.skuId, it.count)
    router.push('/cart')
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div class="container page">
    <div class="top">
      <nav class="tabs">
        <a v-for="t in TABS" :key="t.key" :class="{ on: status === t.key }" @click="status = t.key">
          {{ t.text }}<em v-if="t.key && counts[t.key]">{{ counts[t.key] }}</em>
        </a>
      </nav>
      <form class="search" @submit.prevent="page = 1; load()">
        <input v-model.trim="keyword" class="input" placeholder="商品名称 / 订单号" />
        <button class="btn btn-primary btn-square">搜索</button>
      </form>
    </div>

    <div class="thead">
      <span class="c1">商品</span><span class="c2">单价</span><span class="c3">数量</span><span class="c4">实付款</span><span class="c5">交易状态</span><span class="c6">操作</span>
    </div>

    <div v-for="o in list" :key="o.id" class="order card">
      <div class="o-head">
        <span>{{ formatTime(o.createdAt) }}</span>
        <span class="muted">订单号：<span class="num">{{ o.id }}</span></span>
        <span v-if="o.source === 'SECKILL'" class="tag tag-red"><Icon name="bolt" :size="10" /> 秒杀订单</span>
        <span class="shop"><Icon name="store" :size="14" /> {{ o.items[0]?.name.split(' ')[0] }}旗舰店</span>
      </div>
      <div class="o-body">
        <div class="items">
          <div v-for="it in o.items" :key="it.skuId" class="it">
            <RouterLink :to="`/product/${it.spuId}`" class="c1">
              <ProductArt :kind="it.art" :tint="it.tint" :size="72" bg />
              <div>
                <p class="ellipsis-2">{{ it.name }}</p>
                <span class="muted">{{ it.specText }}</span>
              </div>
            </RouterLink>
            <span class="c2 num">¥{{ yuan(it.price, true) }}</span>
            <span class="c3">× {{ it.count }}</span>
          </div>
        </div>
        <div class="c4 cell">
          <b class="num">¥{{ yuan(o.price.payAmount, true) }}</b>
          <span class="muted">{{ o.price.freight ? `含运费 ¥${yuan(o.price.freight)}` : '免运费' }}</span>
        </div>
        <div class="c5 cell">
          <b :style="{ color: ORDER_STATUS[o.status].color }">{{ ORDER_STATUS[o.status].text }}</b>
          <RouterLink :to="`/orders/${o.id}`" class="link">订单详情</RouterLink>
          <span v-if="o.status === 'SHIPPED' && o.logistics" class="muted lg">{{ o.logistics.traces[0]?.desc.slice(0, 14) }}…</span>
        </div>
        <div class="c6 cell">
          <template v-if="o.status === 'PENDING_PAYMENT'">
            <RouterLink :to="`/pay/${o.id}`" class="btn btn-primary btn-sm">立即付款</RouterLink>
            <span class="cd"><CountdownBox :target="o.payDeadline" theme="plain" @end="load" /></span>
            <a class="link" @click="cancel(o)">取消订单</a>
          </template>
          <template v-else-if="o.status === 'SHIPPED'">
            <button class="btn btn-primary btn-sm" @click="confirmRecv(o)">确认收货</button>
            <RouterLink :to="`/orders/${o.id}`" class="link">查看物流</RouterLink>
          </template>
          <template v-else-if="o.status === 'PAID'">
            <span class="muted">商家备货中</span>
            <a class="link" @click="toast.info('已提醒商家尽快发货')">提醒发货</a>
          </template>
          <button v-else class="btn btn-ghost btn-sm" @click="rebuy(o)">再次购买</button>
        </div>
      </div>
    </div>

    <Empty v-if="!loading && !list.length" icon="order" text="暂无相关订单">
      <RouterLink to="/" class="btn btn-primary">去逛逛</RouterLink>
    </Empty>
    <Pagination :page="page" :size="5" :total="total" @change="(p) => (page = p)" />
  </div>
</template>

<style scoped>
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 0 20px;
  flex-wrap: wrap;
  gap: 6px;
}
.tabs {
  display: flex;
  gap: 28px;
}
.tabs a {
  position: relative;
  padding: 16px 0;
  cursor: pointer;
  font-size: 15px;
}
.tabs a.on {
  color: var(--c-primary);
  font-weight: 700;
}
.tabs a.on::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: var(--c-primary);
  border-radius: 2px;
}
.tabs em {
  position: absolute;
  top: 6px;
  right: -16px;
  font-style: normal;
  font-size: 11px;
  background: var(--c-red);
  color: #fff;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 16px;
}
.search {
  display: flex;
  gap: 8px;
}
.search .input {
  width: 240px;
  height: 34px;
}
.search .btn {
  height: 34px;
}
.thead {
  display: flex;
  padding: 12px 20px;
  font-size: 13px;
  color: var(--c-text-2);
}
.c1 {
  flex: 1;
  display: flex;
  gap: 12px;
  min-width: 0;
  font-size: 13px;
}
.c1 span {
  font-size: 12px;
}
.c2 {
  width: 110px;
}
.c3 {
  width: 70px;
}
.c4 {
  width: 130px;
}
.c5 {
  width: 130px;
}
.c6 {
  width: 130px;
}
.order {
  margin-bottom: 12px;
  overflow: hidden;
}
.o-head {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 20px;
  background: #fafafa;
  font-size: 13px;
}
.shop {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--c-text-2);
}
.o-body {
  display: flex;
  padding: 0 20px;
}
.items {
  flex: 1;
  min-width: 0;
}
.it {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px dashed var(--c-border);
}
.it:last-child {
  border-bottom: none;
}
.cell {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  border-left: 1px solid var(--c-border);
  padding: 14px 0 14px 16px;
  font-size: 13px;
}
.cell .muted {
  font-size: 12px;
}
.link {
  color: var(--c-text-2);
  cursor: pointer;
  font-size: 12px;
}
.link:hover {
  color: var(--c-primary);
}
.c6 .btn {
  width: 96px;
}
.cd {
  background: var(--c-primary);
  border-radius: 4px;
  width: 96px;
  display: flex;
  justify-content: center;
  transform: scale(0.85);
  transform-origin: left;
}
.lg {
  font-size: 11px !important;
}
</style>
