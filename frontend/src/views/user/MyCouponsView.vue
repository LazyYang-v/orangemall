<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { UserCoupon, UserCouponStatus } from '@/types/domain'
import { couponApi } from '@/api/marketing'
import CouponTicket from '@/components/common/CouponTicket.vue'
import Empty from '@/components/common/Empty.vue'

const all = ref<UserCoupon[]>([])
const tab = ref<UserCouponStatus>('UNUSED')
onMounted(async () => (all.value = await couponApi.mine()))
const list = computed(() => all.value.filter((c) => (tab.value === 'USED' ? ['USED', 'LOCKED'].includes(c.status) : c.status === tab.value)))
const count = (s: UserCouponStatus) => all.value.filter((c) => (s === 'USED' ? ['USED', 'LOCKED'].includes(c.status) : c.status === s)).length
const TABS: Array<[UserCouponStatus, string]> = [['UNUSED', '未使用'], ['USED', '已使用'], ['EXPIRED', '已过期']]
</script>

<template>
  <div class="card wrap">
    <nav class="tabs">
      <a v-for="[k, t] in TABS" :key="k" :class="{ on: tab === k }" @click="tab = k">{{ t }}（{{ count(k) }}）</a>
      <RouterLink to="/coupons" class="get">去领券中心 →</RouterLink>
    </nav>
    <div v-if="list.length" class="grid">
      <CouponTicket
        v-for="c in list" :key="c.id" :type="c.type" :amount="c.amount" :threshold="c.threshold" :title="c.title" :scope="c.scope"
        :valid-to="c.validTo" :dim="c.status !== 'UNUSED'" :stamp="c.status === 'USED' ? '已使用' : c.status === 'LOCKED' ? '使用中' : c.status === 'EXPIRED' ? '已过期' : ''"
      >
        <RouterLink v-if="c.status === 'UNUSED'" to="/search" class="btn btn-primary btn-sm">去使用</RouterLink>
      </CouponTicket>
    </div>
    <Empty v-else icon="coupon" text="暂无优惠券" />
  </div>
</template>

<style scoped>
.wrap {
  padding: 6px 22px 22px;
}
.tabs {
  display: flex;
  gap: 26px;
  border-bottom: 1px solid var(--c-border);
  margin-bottom: 16px;
}
.tabs a {
  padding: 12px 0;
  cursor: pointer;
}
.tabs a.on {
  color: var(--c-primary);
  font-weight: 700;
  border-bottom: 2px solid var(--c-primary);
}
.get {
  margin-left: auto;
  color: var(--c-primary);
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
@media (max-width: 900px) {
  .list, .grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
