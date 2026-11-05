<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Order, PayChannel, PayOrderVO } from '@/types/domain'
import { orderApi } from '@/api/order'
import { payApi, type SandboxNotify } from '@/api/pay'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import { PAY_CHANNEL } from '@/utils/dict'
import CountdownBox from '@/components/common/CountdownBox.vue'
import Icon from '@/components/common/Icon.vue'
import Modal from '@/components/common/Modal.vue'
import FakeQr from '@/components/common/FakeQr.vue'

const route = useRoute()
const router = useRouter()
const orderId = route.params.orderId as string
const order = ref<Order | null>(null)
const channel = ref<PayChannel>('ALIPAY')
const pay = ref<PayOrderVO | null>(null)
const creating = ref(false)
const lastNotify = ref<SandboxNotify | null>(null)
const logs = ref<string[]>([])
let poll: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  try {
    order.value = await orderApi.detail(orderId)
    if (order.value.status !== 'PENDING_PAYMENT') router.replace(order.value.status === 'PAID' ? `/pay/result/${orderId}` : `/orders/${orderId}`)
  } catch (e) {
    toast.error((e as Error).message)
  }
})
onUnmounted(() => clearInterval(poll))

const expired = ref(false)
const summary = computed(() => order.value?.items.map((i) => i.name).join('、') ?? '')

async function createPay() {
  creating.value = true
  try {
    pay.value = await payApi.create(orderId, channel.value)
    log(`创建支付流水 ${pay.value.paymentNo}（${PAY_CHANNEL[channel.value]}沙箱），等待扫码…`)
    clearInterval(poll)
    // 前端轮询支付结果（真实环境也可由 WebSocket 推送）
    poll = setInterval(async () => {
      if (!pay.value) return
      const s = await payApi.status(pay.value.paymentNo)
      if (s.status === 'SUCCESS') {
        clearInterval(poll)
        toast.success('支付成功')
        router.replace(`/pay/result/${orderId}`)
      }
    }, 1500)
  } catch (e) {
    toast.error((e as Error).message)
    if (/关闭|状态/.test((e as Error).message)) router.replace(`/orders/${orderId}`)
  } finally {
    creating.value = false
  }
}

function log(t: string) {
  logs.value.unshift(`${new Date().toLocaleTimeString()}  ${t}`)
}

/** 沙箱：模拟扫码 → 支付平台异步回调我方 notify 接口 */
async function sandbox(success: boolean) {
  if (!pay.value) return
  const n = await payApi.sandboxPay(pay.value.paymentNo, success)
  lastNotify.value = n
  log(`支付平台回调：trade_status=${n.tradeStatus} sign=${n.sign}`)
  await sendNotify(n)
}

async function sendNotify(n: SandboxNotify) {
  try {
    const r = await payApi.notify(n)
    log(r.idempotent ? `第 ${r.callbackCount} 次回调：流水已处理，幂等返回 success（不重复入账）` : `验签通过 → 状态机 PENDING → ${n.tradeStatus === 'TRADE_SUCCESS' ? 'SUCCESS' : 'FAILED'}`)
    if (n.tradeStatus !== 'TRADE_SUCCESS') {
      toast.error('支付失败，可重新发起支付')
      pay.value = null
      clearInterval(poll)
    }
  } catch (e) {
    log(`回调处理失败：${(e as Error).message}`)
  }
}

async function tamper() {
  if (!lastNotify.value) return
  await sendNotify({ ...lastNotify.value, amount: lastNotify.value.amount - 100, sign: lastNotify.value.sign })
}
</script>

<template>
  <div class="container page">
    <div v-if="order" class="card head">
      <div class="ok"><Icon name="check-circle" :size="40" /></div>
      <div class="txt">
        <h2>订单提交成功，请尽快付款！</h2>
        <p class="muted">订单号：<span class="num">{{ order.id }}</span>　商品：<span class="ellipsis goods">{{ summary }}</span></p>
        <p v-if="!expired" class="cd">
          请在 <CountdownBox :target="order.payDeadline" theme="red" @end="expired = true" /> 内完成支付，超时订单将自动关闭
        </p>
        <p v-else class="cd red">订单已超时，系统正在关闭订单…</p>
      </div>
      <div class="amount">
        应付金额
        <span class="price">¥{{ yuan(order.price.payAmount, true) }}</span>
      </div>
    </div>

    <div v-if="order" class="card channels">
      <h3>选择支付方式</h3>
      <div class="list">
        <label v-for="c in (['ALIPAY', 'WECHAT'] as PayChannel[])" :key="c" class="ch" :class="{ on: channel === c }">
          <input v-model="channel" type="radio" :value="c" hidden />
          <Icon :name="c === 'ALIPAY' ? 'alipay' : 'wechat'" :size="30" :style="{ color: c === 'ALIPAY' ? '#1677ff' : '#07c160' }" />
          <div>
            <b>{{ PAY_CHANNEL[c] }}</b>
            <p class="muted">{{ c === 'ALIPAY' ? '支付宝开放平台沙箱 · RSA2 验签' : '微信支付 V3 沙箱 · 平台证书验签' }}</p>
          </div>
          <Icon v-if="channel === c" name="check-circle" :size="20" class="ck" />
        </label>
      </div>
      <div class="foot">
        <span>支付 <b class="price">¥{{ yuan(order.price.payAmount, true) }}</b></span>
        <button class="btn btn-primary btn-lg" :disabled="creating || expired" @click="createPay">{{ creating ? '正在创建支付…' : '确认支付' }}</button>
      </div>
    </div>

    <Modal :open="!!pay" :title="`${pay ? PAY_CHANNEL[pay.channel] : ''}扫码支付（沙箱）`" :width="640" @close="pay = null">
      <div v-if="pay" class="qr-wrap">
        <div class="qr">
          <FakeQr :text="pay.qrCode" :size="190" :color="pay.channel === 'ALIPAY' ? '#1677ff' : '#07c160'" />
          <p class="muted">请使用{{ PAY_CHANNEL[pay.channel] }}扫一扫</p>
          <p class="price big">¥{{ yuan(pay.amount, true) }}</p>
          <p class="muted small">流水号 {{ pay.paymentNo }}</p>
        </div>
        <div class="sandbox">
          <h4>沙箱调试台</h4>
          <p class="muted small">真实环境由支付平台服务器回调 <code>POST /api/payments/notify</code>，这里手动触发以演示验签与幂等。</p>
          <div class="btns">
            <button class="btn btn-primary btn-sm" @click="sandbox(true)">模拟扫码支付成功</button>
            <button class="btn btn-sm" @click="sandbox(false)">模拟支付失败</button>
            <button class="btn btn-sm" :disabled="!lastNotify" @click="lastNotify && sendNotify(lastNotify)">重复回调（幂等）</button>
            <button class="btn btn-sm" :disabled="!lastNotify" @click="tamper">篡改金额（验签失败）</button>
          </div>
          <ul class="logs">
            <li v-for="(l, i) in logs" :key="i">{{ l }}</li>
          </ul>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 26px 30px;
}
.ok {
  color: var(--c-success);
}
.txt {
  flex: 1;
  min-width: 0;
}
.txt h2 {
  font-size: 20px;
}
.txt p {
  margin-top: 6px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.goods {
  max-width: 480px;
  display: inline-block;
}
.red {
  color: var(--c-red);
}
.amount {
  text-align: right;
  color: var(--c-text-2);
}
.amount .price {
  display: block;
  font-size: 30px;
}
.channels {
  margin-top: 14px;
  padding: 22px 30px;
}
.channels h3 {
  font-size: 16px;
  margin-bottom: 14px;
}
.list {
  display: flex;
  gap: 16px;
}
.ch {
  position: relative;
  width: 320px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1.5px solid var(--c-border-2);
  border-radius: var(--radius);
  cursor: pointer;
}
.ch.on {
  border-color: var(--c-primary);
  background: var(--c-primary-soft);
}
.ch p {
  font-size: 12px;
}
.ck {
  position: absolute;
  right: 12px;
  color: var(--c-primary);
}
.foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  margin-top: 24px;
}
.foot .price {
  font-size: 22px;
}
.qr-wrap {
  display: flex;
  gap: 24px;
}
.qr {
  text-align: center;
  padding: 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
}
.qr p {
  margin-top: 6px;
}
.big {
  font-size: 24px;
}
.small {
  font-size: 12px;
}
.sandbox {
  flex: 1;
  min-width: 0;
}
.sandbox h4 {
  margin-bottom: 6px;
}
code {
  background: #f5f5f5;
  padding: 1px 4px;
  border-radius: 3px;
}
.btns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 12px 0;
}
.logs {
  height: 150px;
  overflow: auto;
  background: #1e1e1e;
  color: #9fe29f;
  border-radius: 8px;
  padding: 8px 10px;
  font-family: Menlo, monospace;
  font-size: 11px;
  line-height: 1.7;
}
</style>
