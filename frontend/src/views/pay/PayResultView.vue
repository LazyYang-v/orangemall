<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { Order } from '@/types/domain'
import { orderApi } from '@/api/order'
import { yuan, formatTime } from '@/utils/format'
import { PAY_CHANNEL } from '@/utils/dict'
import Icon from '@/components/common/Icon.vue'

const route = useRoute()
const order = ref<Order | null>(null)
onMounted(async () => (order.value = await orderApi.detail(route.params.orderId as string)))

const FLOW = ['创建支付流水（payment_no 唯一索引）', '支付平台异步回调 + 验签', '幂等校验：UPDATE … WHERE status = PENDING', '订单状态机 PENDING_PAYMENT → PAID', 'MQ 通知仓储发货 / 积分 / 消息推送']
</script>

<template>
  <div class="container page">
    <div v-if="order" class="card box">
      <div class="ic"><Icon name="check" :size="44" :stroke="3" /></div>
      <h2>支付成功！</h2>
      <p class="muted">我们会尽快为您发货，物流进度将实时推送给您</p>
      <dl>
        <div><dt>订单号</dt><dd class="num">{{ order.id }}</dd></div>
        <div><dt>支付方式</dt><dd>{{ order.payChannel ? PAY_CHANNEL[order.payChannel] : '-' }}</dd></div>
        <div><dt>支付流水</dt><dd class="num">{{ order.paymentNo }}</dd></div>
        <div><dt>支付时间</dt><dd>{{ formatTime(order.paidAt, true) }}</dd></div>
        <div><dt>实付金额</dt><dd class="price">¥{{ yuan(order.price.payAmount, true) }}</dd></div>
      </dl>
      <div class="btns">
        <RouterLink :to="`/orders/${order.id}`" class="btn btn-primary btn-lg">查看订单</RouterLink>
        <RouterLink to="/" class="btn btn-lg">继续购物</RouterLink>
      </div>
      <ol class="flow">
        <li v-for="(f, i) in FLOW" :key="i"><span>{{ i + 1 }}</span>{{ f }}</li>
      </ol>
    </div>
  </div>
</template>

<style scoped>
.box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 50px 0 36px;
}
.ic {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--c-success);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 24px rgba(18, 161, 80, 0.3);
}
h2 {
  margin-top: 18px;
  font-size: 24px;
}
dl {
  margin: 24px 0;
  width: 420px;
  font-size: 13px;
  background: #fafafa;
  border-radius: var(--radius);
  padding: 12px 20px;
}
dl div {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
}
dt {
  color: var(--c-text-3);
}
dd {
  margin: 0;
}
.btns {
  display: flex;
  gap: 16px;
}
.flow {
  display: flex;
  gap: 8px;
  margin-top: 36px;
  padding: 0;
  list-style: none;
  font-size: 12px;
  color: var(--c-text-3);
}
.flow li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #fafafa;
  border-radius: 999px;
}
.flow span {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--c-primary-soft);
  color: var(--c-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
</style>
