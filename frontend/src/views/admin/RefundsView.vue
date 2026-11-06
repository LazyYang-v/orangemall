<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { RefundOrder, RefundStatus } from '@/types/domain'
import { adminRefundApi } from '@/api/refund'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { yuan, formatTime } from '@/utils/format'
import { ORDER_STATUS, REFUND_STATUS, REFUND_TYPE } from '@/utils/dict'
import Modal from '@/components/common/Modal.vue'
import Pagination from '@/components/common/Pagination.vue'

const q = reactive({ status: '' as RefundStatus | '', page: 1, size: 10 })
const list = ref<RefundOrder[]>([])
const total = ref(0)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const r = await adminRefundApi.list(q)
    list.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
function search() {
  q.page = 1
  load()
}

const STATUS_OPTS = Object.entries(REFUND_STATUS) as Array<[RefundStatus, { text: string }]>
const auditing = ref<RefundOrder | null>(null)
const form = reactive({ remark: '' })
async function approve(pass: boolean) {
  if (!auditing.value) return
  try {
    await adminRefundApi.approve(auditing.value.refundId, { pass, remark: form.remark })
    toast.success(pass ? '审批通过，退款将原路退回（沙箱渠道 2s 后回调完成）' : '已拒绝，订单将回退到原状态')
    auditing.value = null
    form.remark = ''
    load()
    if (pass) setTimeout(load, 4000)
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>售后管理</h2><span class="sub">审批通过后：渠道退款 → 库存回补 → 订单置为已退款</span></div>
    <div class="adm-card">
      <div class="adm-toolbar">
        <select v-model="q.status" @change="search">
          <option value="">全部状态</option>
          <option v-for="[k, v] in STATUS_OPTS" :key="k" :value="k">{{ v.text }}</option>
        </select>
        <button class="btn btn-primary btn-sm btn-square" @click="search">查询</button>
      </div>
      <table class="table">
        <thead>
          <tr><th>售后单号</th><th>类型</th><th>关联订单</th><th>金额</th><th>原因</th><th>状态</th><th>申请时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.refundId">
            <td class="mono">{{ r.refundId }}</td>
            <td><span class="tag tag-gray">{{ REFUND_TYPE[r.type] }}</span></td>
            <td class="mono">{{ r.orderId }}</td>
            <td class="price">¥{{ yuan(r.amount, true) }}</td>
            <td class="ellipsis" style="max-width: 180px" :title="r.reason">{{ r.reason }}</td>
            <td><span class="dot" :style="{ background: REFUND_STATUS[r.status]?.color }" />{{ REFUND_STATUS[r.status]?.text }}</td>
            <td class="num muted">{{ formatTime(r.createdAt) }}</td>
            <td>
              <button v-if="r.status === 'WAIT_APPROVAL'" class="op" @click="auditing = r; form.remark = r.auditRemark || ''">审批</button>
              <span v-else-if="r.auditRemark" class="muted" :title="r.auditRemark">{{ r.auditRemark.slice(0, 8) }}…</span>
            </td>
          </tr>
          <tr v-if="!list.length && !loading"><td colspan="8" class="muted" style="text-align: center; padding: 40px">暂无售后单</td></tr>
        </tbody>
      </table>
      <Pagination :page="q.page" :size="q.size" :total="total" @change="(p) => { q.page = p; load() }" />
    </div>

    <Modal :open="!!auditing" title="售后审批" @close="auditing = null">
      <p class="muted" style="margin-bottom: 10px">
        售后单 <span class="mono">{{ auditing?.refundId }}</span> · 订单 <span class="mono">{{ auditing?.orderId }}</span> ·
        {{ auditing && REFUND_TYPE[auditing.type] }} · 退款 ¥{{ auditing && yuan(auditing.amount, true) }}
      </p>
      <p style="margin-bottom: 12px">用户原因：{{ auditing?.reason }}</p>
      <p class="muted" style="margin-bottom: 6px">申请时订单状态：<b>{{ auditing && ORDER_STATUS[auditing.prevStatus as keyof typeof ORDER_STATUS]?.text }}</b>，拒绝/取消后自动回退</p>
      <div class="field">
        <label>审批备注</label>
        <input v-model.trim="form.remark" class="input" maxlength="200" placeholder="如：与用户核实破损照片后同意退款" />
      </div>
      <template #footer>
        <button class="btn" @click="approve(false)">拒绝</button>
        <button class="btn btn-primary" @click="approve(true)">通过退款</button>
      </template>
    </Modal>
  </div>
</template>
