<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { CouponTemplate, CouponType } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { toast } from '@/utils/toast'
import { yuan, shortDate } from '@/utils/format'
import { COUPON_TYPE } from '@/utils/dict'
import Modal from '@/components/common/Modal.vue'
import Icon from '@/components/common/Icon.vue'

const list = ref<CouponTemplate[]>([])
async function load() {
  list.value = await adminApi.coupons()
}
onMounted(load)

const open = ref(false)
const blank = () => ({ title: '', type: 'FULL_REDUCTION' as CouponType, threshold: 199, amount: 20, scope: '全场通用（秒杀商品除外）', total: 1000, validDays: 7, perUserLimit: 1, days: 7 })
const f = reactive(blank())
function create() {
  Object.assign(f, blank())
  open.value = true
}
async function submit() {
  if (!f.title) return toast.warning('请填写券名称')
  if (f.type === 'DISCOUNT' && (f.amount <= 0 || f.amount >= 10)) return toast.warning('折扣需在 0~10 折之间')
  const now = Date.now()
  await adminApi.createCoupon({
    title: f.title,
    type: f.type,
    threshold: f.type === 'NO_THRESHOLD' ? 0 : Math.round(f.threshold * 100),
    amount: f.type === 'DISCOUNT' ? Math.round(f.amount * 10) : Math.round(f.amount * 100),
    scope: f.scope,
    total: f.total,
    validDays: f.validDays,
    perUserLimit: f.perUserLimit,
    startAt: new Date(now).toISOString(),
    endAt: new Date(now + f.days * 86400000).toISOString(),
  })
  toast.success('已创建，领券中心可见')
  open.value = false
  load()
}
const face = (c: CouponTemplate) => (c.type === 'DISCOUNT' ? `${c.amount / 10} 折` : `¥${yuan(c.amount)}`)
const rule = (c: CouponTemplate) => (c.threshold ? `满 ${yuan(c.threshold)} 可用` : '无门槛')
const usage = (c: CouponTemplate) => Math.min(100, (c.received / c.total) * 100)
</script>

<template>
  <div class="adm-page">
    <div class="adm-title">
      <h2>优惠券模板</h2><span class="sub">领券走 Redis 原子扣减「剩余量」+ 用户维度限领；下单锁券，取消/超时回滚</span>
      <div class="right"><button class="btn btn-primary btn-sm btn-square" @click="create"><Icon name="plus" :size="14" /> 新建券</button></div>
    </div>
    <div class="adm-card">
      <table class="table">
        <thead><tr><th>模板 ID</th><th>名称</th><th>类型</th><th>面额</th><th>门槛</th><th>适用范围</th><th style="width: 180px">发放进度</th><th>每人限领</th><th>领取后有效</th><th>活动截止</th></tr></thead>
        <tbody>
          <tr v-for="c in list" :key="c.id">
            <td class="mono">{{ c.id }}</td>
            <td>{{ c.title }}</td>
            <td><span class="tag">{{ COUPON_TYPE[c.type] }}</span></td>
            <td class="price">{{ face(c) }}</td>
            <td>{{ rule(c) }}</td>
            <td class="muted">{{ c.scope }}</td>
            <td>
              <div class="prog"><i :style="{ width: usage(c) + '%' }" /></div>
              <span class="num muted pn">{{ c.received }} / {{ c.total }}</span>
            </td>
            <td class="num">{{ c.perUserLimit }}</td>
            <td class="num">{{ c.validDays }} 天</td>
            <td class="num muted">{{ shortDate(c.endAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal :open="open" title="新建优惠券模板" :width="520" @close="open = false">
      <div class="form">
        <div class="field full"><label>券名称</label><input v-model.trim="f.title" class="input" placeholder="如：双11 家居专享券" /></div>
        <div class="field">
          <label>类型</label>
          <select v-model="f.type" class="input"><option v-for="(t, k) in COUPON_TYPE" :key="k" :value="k">{{ t }}</option></select>
        </div>
        <div class="field">
          <label>{{ f.type === 'DISCOUNT' ? '折扣（如 8.5 折）' : '减免金额（元）' }}</label>
          <input v-model.number="f.amount" class="input" type="number" min="0" :step="f.type === 'DISCOUNT' ? 0.5 : 1" />
        </div>
        <div v-if="f.type !== 'NO_THRESHOLD'" class="field"><label>使用门槛（元）</label><input v-model.number="f.threshold" class="input" type="number" min="0" /></div>
        <div class="field"><label>发放总量</label><input v-model.number="f.total" class="input" type="number" min="1" /></div>
        <div class="field"><label>每人限领</label><input v-model.number="f.perUserLimit" class="input" type="number" min="1" /></div>
        <div class="field"><label>领取后有效（天）</label><input v-model.number="f.validDays" class="input" type="number" min="1" /></div>
        <div class="field"><label>活动持续（天）</label><input v-model.number="f.days" class="input" type="number" min="1" /></div>
        <div class="field full"><label>适用范围说明</label><input v-model.trim="f.scope" class="input" /></div>
      </div>
      <template #footer>
        <button class="btn" @click="open = false">取消</button>
        <button class="btn btn-primary" @click="submit">创建</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.prog {
  height: 6px;
  background: #f1f2f4;
  border-radius: 3px;
  overflow: hidden;
}
.prog i {
  display: block;
  height: 100%;
  background: #ff6a1a;
  border-radius: 3px;
}
.pn {
  font-size: 11px;
}
.form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 14px;
}
.full {
  grid-column: 1 / -1;
}
</style>
