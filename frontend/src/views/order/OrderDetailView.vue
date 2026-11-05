<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { Order } from '@/types/domain'
import { orderApi } from '@/api/order'
import { useNotify } from '@/composables/useNotify'
import { toast } from '@/utils/toast'
import { yuan, formatTime, maskPhone } from '@/utils/format'
import { ORDER_STATUS, PAY_CHANNEL } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import CountdownBox from '@/components/common/CountdownBox.vue'
import Icon from '@/components/common/Icon.vue'
import LogisticsTimeline from '@/components/order/LogisticsTimeline.vue'

const route = useRoute()
const id = route.params.id as string
const order = ref<Order | null>(null)
const live = ref(false)

async function load() {
  order.value = await orderApi.detail(id)
}
onMounted(load)

useNotify((m) => {
  if (('orderId' in m && m.orderId !== id) || m.type === 'SECKILL_RESULT') return
  live.value = true
  setTimeout(() => (live.value = false), 1500)
  if (m.type === 'LOGISTICS_UPDATE') toast.info(`物流更新：${m.trace.desc}`)
  load()
})

const STEPS = [
  { key: 'createdAt', text: '提交订单' },
  { key: 'paidAt', text: '付款成功' },
  { key: 'shippedAt', text: '商家发货' },
  { key: 'completedAt', text: '确认收货' },
] as const
const closed = computed(() => order.value && ['CLOSED', 'CANCELLED'].includes(order.value.status))
const stepIdx = computed(() => (order.value ? STEPS.filter((s) => order.value![s.key]).length - 1 : 0))

async function cancel() {
  if (!confirm('确定取消该订单？')) return
  await orderApi.cancel(id)
  toast.success('订单已取消')
  load()
}
async function confirmRecv() {
  const r = await orderApi.confirm(id)
  toast.success(`确认收货成功，获得 ${r.earnedPoints} 积分`)
  load()
}
</script>

<template>
  <div class="container page">
    <div class="breadcrumb">
      <RouterLink to="/orders">我的订单</RouterLink><Icon name="chevron-right" :size="12" /><span>订单详情</span>
    </div>
    <template v-if="order">
      <section class="card status">
        <div class="left">
          <p class="muted">订单号：<span class="num">{{ order.id }}</span></p>
          <h2 :style="{ color: ORDER_STATUS[order.status].color }">{{ ORDER_STATUS[order.status].text }}</h2>
          <template v-if="order.status === 'PENDING_PAYMENT'">
            <p class="tip">剩余 <CountdownBox :target="order.payDeadline" @end="load" /> 自动关闭</p>
            <div class="btns">
              <RouterLink :to="`/pay/${order.id}`" class="btn btn-primary">立即付款</RouterLink>
              <button class="btn" @click="cancel">取消订单</button>
            </div>
          </template>
          <p v-else-if="closed" class="tip muted">{{ order.closeReason }}</p>
          <div v-else-if="order.status === 'SHIPPED'" class="btns">
            <button class="btn btn-primary" @click="confirmRecv">确认收货</button>
          </div>
          <p v-else-if="order.status === 'PAID'" class="tip muted">商家正在备货，发货后物流轨迹会实时推送</p>
        </div>
        <ol v-if="!closed" class="steps">
          <li v-for="(s, i) in STEPS" :key="s.key" :class="{ done: i <= stepIdx, cur: i === stepIdx }">
            <span class="dot"><Icon v-if="i <= stepIdx" name="check" :size="14" :stroke="3" /><template v-else>{{ i + 1 }}</template></span>
            <b>{{ s.text }}</b>
            <time class="muted">{{ order[s.key] ? formatTime(order[s.key]) : '' }}</time>
          </li>
        </ol>
        <div v-else class="closed-ic"><Icon name="close" :size="48" /></div>
      </section>

      <div class="cols">
        <section class="card block">
          <h3>
            物流信息
            <span class="live" :class="{ on: live }"><i /> WebSocket 实时推送</span>
          </h3>
          <LogisticsTimeline v-if="order.logistics" :info="order.logistics" />
          <p v-else class="muted">{{ order.status === 'PAID' ? '商家备货中，暂无物流信息' : '暂无物流信息' }}</p>
        </section>
        <section class="card block info">
          <h3>订单信息</h3>
          <dl>
            <div><dt>收货人</dt><dd>{{ order.address.receiver }}　{{ maskPhone(order.address.phone) }}</dd></div>
            <div><dt>收货地址</dt><dd>{{ order.address.province }}{{ order.address.city }}{{ order.address.district }} {{ order.address.detail }}</dd></div>
            <div><dt>订单来源</dt><dd>{{ order.source === 'SECKILL' ? '限时秒杀' : '普通下单' }}</dd></div>
            <div><dt>下单时间</dt><dd>{{ formatTime(order.createdAt, true) }}</dd></div>
            <div v-if="order.paidAt"><dt>支付方式</dt><dd>{{ order.payChannel ? PAY_CHANNEL[order.payChannel] : '-' }}（{{ formatTime(order.paidAt, true) }}）</dd></div>
            <div v-if="order.paymentNo"><dt>支付流水号</dt><dd class="num">{{ order.paymentNo }}</dd></div>
            <div v-if="order.remark"><dt>订单备注</dt><dd>{{ order.remark }}</dd></div>
          </dl>
        </section>
      </div>

      <section class="card block">
        <h3>商品信息</h3>
        <div v-for="it in order.items" :key="it.skuId" class="item">
          <ProductArt :kind="it.art" :tint="it.tint" :size="72" bg />
          <RouterLink :to="`/product/${it.spuId}`" class="nm">
            <p>{{ it.name }}</p>
            <span class="muted">{{ it.specText }}</span>
          </RouterLink>
          <span class="num">¥{{ yuan(it.price, true) }} × {{ it.count }}</span>
          <b class="price">¥{{ yuan(it.price * it.count, true) }}</b>
        </div>
        <dl class="sum">
          <div><dt>商品总额</dt><dd>¥{{ yuan(order.price.goodsAmount, true) }}</dd></div>
          <div><dt>运费</dt><dd>¥{{ yuan(order.price.freight, true) }}</dd></div>
          <div v-if="order.price.seckillDeduct"><dt>秒杀优惠</dt><dd class="red">-¥{{ yuan(order.price.seckillDeduct, true) }}</dd></div>
          <div v-if="order.price.fullReduction"><dt>跨店满减</dt><dd class="red">-¥{{ yuan(order.price.fullReduction, true) }}</dd></div>
          <div v-if="order.price.couponDeduct"><dt>优惠券</dt><dd class="red">-¥{{ yuan(order.price.couponDeduct, true) }}</dd></div>
          <div v-if="order.price.pointsDeduct"><dt>积分抵扣（{{ order.pointsUsed }}）</dt><dd class="red">-¥{{ yuan(order.price.pointsDeduct, true) }}</dd></div>
          <div class="total"><dt>{{ order.paidAt ? '实付款' : '应付款' }}</dt><dd class="price">¥{{ yuan(order.price.payAmount, true) }}</dd></div>
        </dl>
      </section>
    </template>
    <div v-else class="card skeleton" style="height: 300px" />
  </div>
</template>

<style scoped>
.status {
  display: flex;
  align-items: center;
  padding: 26px 30px;
  gap: 40px;
}
.left {
  width: 300px;
  flex: none;
}
.left p {
  font-size: 13px;
}
h2 {
  font-size: 26px;
  margin: 6px 0;
}
.tip {
  display: flex;
  align-items: center;
  gap: 6px;
}
.btns {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.steps {
  flex: 1;
  display: flex;
  list-style: none;
  padding: 0;
  margin: 0;
}
.steps li {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-3);
}
.steps li::before {
  content: '';
  position: absolute;
  top: 14px;
  left: -50%;
  width: 100%;
  height: 2px;
  background: var(--c-border-2);
  z-index: 0;
}
.steps li:first-child::before {
  display: none;
}
.steps li.done::before {
  background: var(--c-primary);
}
.dot {
  position: relative;
  z-index: 1;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #eee;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
.done .dot {
  background: var(--c-primary);
  color: #fff;
}
.done b {
  color: var(--c-text);
}
.cur .dot {
  box-shadow: 0 0 0 5px var(--c-primary-soft);
}
time {
  font-size: 11px;
  height: 14px;
}
.closed-ic {
  flex: 1;
  text-align: center;
  color: #ddd;
}
.cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 14px;
}
.block {
  padding: 18px 22px;
}
.cols + .block {
  margin-top: 14px;
}
.block h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  margin-bottom: 14px;
}
.live {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--c-text-3);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.live i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--c-success);
}
.live.on i {
  animation: blink 0.3s 4;
}
@keyframes blink {
  50% { transform: scale(1.8); opacity: 0.4; }
}
dl {
  margin: 0;
  font-size: 13px;
}
dl div {
  display: flex;
  padding: 5px 0;
}
dt {
  width: 90px;
  flex: none;
  color: var(--c-text-3);
}
dd {
  margin: 0;
}
.item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--c-border);
}
.nm {
  flex: 1;
  font-size: 13px;
}
.nm span {
  font-size: 12px;
}
.item .price {
  width: 110px;
  text-align: right;
}
.sum {
  width: 300px;
  margin: 14px 0 0 auto;
}
.sum div {
  justify-content: space-between;
}
.sum dd {
  font-family: var(--font-num);
}
.red {
  color: var(--c-red);
}
.total {
  border-top: 1px solid var(--c-border);
  margin-top: 6px;
  padding-top: 10px !important;
  align-items: baseline;
}
.total .price {
  font-size: 22px;
}
</style>
