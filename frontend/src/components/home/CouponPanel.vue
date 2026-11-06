<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { CouponTemplate } from '@/types/domain'
import { couponApi } from '@/api/marketing'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import Icon from '../common/Icon.vue'

defineProps<{ coupons: CouponTemplate[] }>()
const emit = defineEmits<{ received: [] }>()
const user = useUserStore()
const router = useRouter()

async function receive(t: CouponTemplate) {
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: '/' } })
  try {
    await couponApi.receive(t.id)
    toast.success(`领取成功：${t.title}`)
    emit('received')
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <section class="panel">
    <header>
      <h3><span class="ic"><Icon name="dollar" :size="16" :stroke="2.2" /></span>领券中心</h3>
      <span class="sub">大额神券 · 下单立减</span>
      <RouterLink to="/coupons" class="more">更多 <Icon name="chevron-right" :size="12" /></RouterLink>
    </header>
    <div class="list">
      <div v-for="c in coupons" :key="c.id" class="coupon">
        <div class="top">
          <span class="amt"><small>¥</small>{{ yuan(c.amount) }}</span>
          <span class="cond">满{{ yuan(c.threshold) }}可用</span>
        </div>
        <button class="get" :class="{ done: c.receivedByMe }" :disabled="c.receivedByMe || c.received >= c.total" @click="receive(c)">
          {{ c.receivedByMe ? '已领取' : c.received >= c.total ? '已抢光' : '立即领取' }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.panel {
  background: linear-gradient(160deg, #ffe7da 0%, #fff4ee 60%, #fff 100%);
  border-radius: var(--radius-lg);
  padding: 14px 18px 16px;
  border: 1px solid #ffe0cc;
}
header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 800;
  color: var(--c-primary);
}
.ic {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: var(--g-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.sub {
  font-size: 13px;
  color: var(--c-primary-dark);
}
.more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  color: var(--c-text-2);
}
.list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.coupon {
  position: relative;
  background: #fff;
  border-radius: 10px;
  padding: 16px 12px 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  box-shadow: 0 4px 12px rgba(255, 90, 0, 0.08);
}
.coupon::before,
.coupon::after {
  content: '';
  position: absolute;
  top: 42%;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #ff6a2a;
}
.coupon::before {
  left: -6px;
}
.coupon::after {
  right: -6px;
}
.top {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.amt {
  color: var(--c-price);
  font-family: var(--font-num);
  font-size: 34px;
  font-weight: 800;
  line-height: 1;
}
.amt small {
  font-size: 18px;
}
.cond {
  font-size: 13px;
  color: var(--c-primary-dark);
}
.get {
  height: 28px;
  padding: 0 22px;
  border-radius: 999px;
  border: none;
  background: var(--g-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}
.get:hover:not(:disabled) {
  filter: brightness(1.06);
}
.get:disabled {
  background: #ffd2b5;
  cursor: default;
}
@media (max-width: 900px) {
  .list > * { min-width: 0; }
}
</style>
