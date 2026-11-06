<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { Order, OrderStatus } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { yuan, formatTime, maskPhone } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/dict'
import Modal from '@/components/common/Modal.vue'
import Pagination from '@/components/common/Pagination.vue'
import ProductArt from '@/components/common/ProductArt.vue'

const route = useRoute()
const q = reactive({ status: (route.query.status as OrderStatus) || ('' as OrderStatus | ''), source: '', keyword: '', page: 1, size: 10 })
const list = ref<Order[]>([])
const total = ref(0)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const r = await adminApi.orders(q)
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

const COMPANIES = ['顺丰速运', '京东物流', '中通快递', '圆通速递']
const shipping = ref<Order | null>(null)
const shipForm = reactive({ company: COMPANIES[0], trackingNo: '' })
async function doShip() {
  if (!shipping.value) return
  try {
    await adminApi.ship(shipping.value.id, shipForm)
    toast.success('已发货，用户端将实时收到物流推送')
    shipping.value = null
    load()
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
async function expire(o: Order) {
  try {
    await adminApi.expire(o.id)
    toast.success('延迟消息已到期：订单关闭、库存与优惠券已回滚')
    load()
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
const STATUS_OPTS = Object.entries(ORDER_STATUS) as Array<[OrderStatus, { text: string }]>
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>订单管理</h2><span class="sub">共 {{ total }} 单 · 按 user_id 分片（Mock 为单表）</span></div>
    <div class="adm-card">
      <div class="adm-toolbar">
        <select v-model="q.status" @change="search">
          <option value="">全部状态</option>
          <option v-for="[k, v] in STATUS_OPTS" :key="k" :value="k">{{ v.text }}</option>
        </select>
        <select v-model="q.source" @change="search">
          <option value="">全部来源</option>
          <option value="NORMAL">普通订单</option>
          <option value="SECKILL">秒杀订单</option>
        </select>
        <input v-model.trim="q.keyword" class="input" style="width: 260px" placeholder="完整订单号 / 收货人或手机号开头" title="订单号精确匹配；收货人、手机号按前缀匹配（走索引）" @keyup.enter="search" />
        <button class="btn btn-primary btn-sm btn-square" @click="search">查询</button>
      </div>
      <table class="table">
        <thead>
          <tr><th>订单号</th><th>商品</th><th>收货人</th><th>实付</th><th>来源</th><th>状态</th><th>下单时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="o in list" :key="o.id">
            <td class="mono">{{ o.id }}</td>
            <td>
              <div class="goods">
                <ProductArt :kind="o.items[0].art" :tint="o.items[0].tint" :size="40" bg />
                <span class="ellipsis" :title="o.items.map((i) => i.name).join('、')">{{ o.items[0].name }}<template v-if="o.items.length > 1"> 等 {{ o.items.length }} 件</template></span>
              </div>
            </td>
            <td>{{ o.address.receiver }}<br /><span class="muted num">{{ maskPhone(o.address.phone) }}</span></td>
            <td class="price">¥{{ yuan(o.price.payAmount, true) }}</td>
            <td><span class="tag" :class="o.source === 'SECKILL' ? 'tag-red' : 'tag-gray'">{{ o.source === 'SECKILL' ? '秒杀' : '普通' }}</span></td>
            <td><span class="dot" :style="{ background: ORDER_STATUS[o.status].color }" />{{ ORDER_STATUS[o.status].text }}</td>
            <td class="num muted">{{ formatTime(o.createdAt) }}</td>
            <td>
              <button v-if="o.status === 'PAID'" class="op" @click="shipping = o">发货</button>
              <button v-if="o.status === 'PENDING_PAYMENT'" class="op danger" title="把 TTL 置为到期，触发死信队列关单" @click="expire(o)">模拟超时关单</button>
              <span v-if="o.logistics" class="muted mono">{{ o.logistics.trackingNo }}</span>
            </td>
          </tr>
          <tr v-if="!list.length && !loading"><td colspan="8" class="muted" style="text-align: center; padding: 40px">暂无订单</td></tr>
        </tbody>
      </table>
      <Pagination :page="q.page" :size="q.size" :total="total" @change="(p) => { q.page = p; load() }" />
    </div>

    <Modal :open="!!shipping" title="订单发货" @close="shipping = null">
      <p class="muted" style="margin-bottom: 12px">订单 <span class="mono">{{ shipping?.id }}</span></p>
      <div class="field">
        <label>物流公司</label>
        <select v-model="shipForm.company" class="input"><option v-for="c in COMPANIES" :key="c">{{ c }}</option></select>
      </div>
      <div class="field"><label>运单号（留空自动生成）</label><input v-model.trim="shipForm.trackingNo" class="input" placeholder="SF..." /></div>
      <template #footer>
        <button class="btn" @click="shipping = null">取消</button>
        <button class="btn btn-primary" @click="doShip">确认发货</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.goods {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 240px;
}
</style>
