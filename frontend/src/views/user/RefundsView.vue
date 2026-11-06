<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { RefundOrder } from '@/types/domain'
import { refundApi } from '@/api/refund'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { yuan, formatTime } from '@/utils/format'
import { REFUND_STATUS, REFUND_TYPE } from '@/utils/dict'
import Icon from '@/components/common/Icon.vue'
import Empty from '@/components/common/Empty.vue'

const list = ref<RefundOrder[]>([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    list.value = await refundApi.list({ page: 1, size: 50 })
  } finally {
    loading.value = false
  }
}
onMounted(load)
async function cancel(r: RefundOrder) {
  if (!confirm('撤回该售后申请？')) return
  try {
    await refundApi.cancel(r.refundId)
    toast.success('已撤回售后申请，订单恢复至原状态')
    load()
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
</script>

<template>
  <div>
    <section class="card">
      <h2 style="margin-bottom: 14px">我的售后</h2>
      <template v-if="list.length">
        <div v-for="r in list" :key="r.refundId" class="rf">
          <div class="rf-main">
            <p class="muted">订单 <span class="num">{{ r.orderId }}</span></p>
            <p style="font-weight: 600">{{ REFUND_TYPE[r.type] }} · ¥{{ yuan(r.amount, true) }}</p>
            <p class="muted ellipsis" style="max-width: 100%">原因：{{ r.reason }}</p>
          </div>
          <div class="rf-side">
            <span class="dot" :style="{ background: REFUND_STATUS[r.status].color }" />{{ REFUND_STATUS[r.status].text }}
            <time class="muted">{{ formatTime(r.createdAt) }}</time>
            <button v-if="r.status === 'WAIT_APPROVAL'" class="op danger" @click="cancel(r)">撤回申请</button>
            <span v-if="r.auditRemark" class="muted" style="font-size: 12px">备注：{{ r.auditRemark }}</span>
          </div>
        </div>
      </template>
      <Empty v-else-if="!loading" icon="order" text="暂无售后单，可从订单详情页发起" />
      <div v-else class="skeleton" style="height: 200px" />
      <p class="muted tipcfg"><Icon name="info" :size="13" /> 已退款金额将原路退回支付账户；秒杀订单同样支持售后。</p>
    </section>
  </div>
</template>

<style scoped>
.rf {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f2;
}
.rf:last-of-type {
  border-bottom: none;
}
.rf-main {
  flex: 1;
  min-width: 0;
}
.rf-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  font-size: 13px;
  white-space: nowrap;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.op {
  background: none;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}
.op.danger {
  color: #e02020;
  border-color: #ffd7d7;
}
.tipcfg {
  margin-top: 14px;
  display: flex;
  gap: 6px;
  align-items: center;
}
@media (max-width: 900px) {
  .rf { flex-wrap: wrap; }
  .rf-main { flex-basis: 100%; }
  .rf-side { flex-direction: row; flex-wrap: wrap; align-items: center; gap: 10px; white-space: normal; }
}
</style>
