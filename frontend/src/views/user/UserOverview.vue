<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Order, UserCoupon } from '@/types/domain'
import { orderApi } from '@/api/order'
import { couponApi } from '@/api/marketing'
import { useUserStore } from '@/stores/user'
import { yuan, formatTime, maskPhone } from '@/utils/format'
import { ORDER_STATUS } from '@/utils/dict'
import ProductArt from '@/components/common/ProductArt.vue'
import Icon from '@/components/common/Icon.vue'

const user = useUserStore()
const counts = ref<Record<string, number>>({})
const recent = ref<Order[]>([])
const coupons = ref<UserCoupon[]>([])
onMounted(async () => {
  user.fetchProfile()
  const [c, r, cp] = await Promise.all([orderApi.counts(), orderApi.list({ page: 1, size: 3 }), couponApi.mine('UNUSED')])
  counts.value = c
  recent.value = r.records
  coupons.value = cp
})
const STATS = [
  { key: 'PENDING_PAYMENT', text: '待付款', icon: 'card' },
  { key: 'PAID', text: '待发货', icon: 'package' },
  { key: 'SHIPPED', text: '待收货', icon: 'truck' },
  { key: 'COMPLETED', text: '已完成', icon: 'check-circle' },
]
</script>

<template>
  <div>
    <section class="card hero">
      <div>
        <h2>{{ user.profile?.nickname }}</h2>
        <p class="muted">手机号 {{ maskPhone(user.profile?.phone ?? '') }} · 用户 ID <span class="num">{{ user.profile?.id }}</span></p>
      </div>
      <div class="kpis">
        <RouterLink to="/points"><b class="num">{{ user.profile?.points ?? 0 }}</b><span>积分</span></RouterLink>
        <RouterLink to="/user/coupons"><b class="num">{{ coupons.length }}</b><span>可用优惠券</span></RouterLink>
        <div><b class="num">{{ user.profile?.creditScore ?? '-' }}</b><span>信用分</span></div>
      </div>
    </section>
    <section class="card stats">
      <RouterLink v-for="s in STATS" :key="s.key" :to="`/orders?status=${s.key}`" class="st">
        <span class="ic"><Icon :name="s.icon" :size="28" :stroke="1.5" /><em v-if="counts[s.key]">{{ counts[s.key] }}</em></span>
        <span>{{ s.text }}</span>
      </RouterLink>
    </section>
    <section class="card block">
      <h3>最近订单 <RouterLink to="/orders" class="more">全部订单 <Icon name="chevron-right" :size="12" /></RouterLink></h3>
      <RouterLink v-for="o in recent" :key="o.id" :to="`/orders/${o.id}`" class="ro">
        <ProductArt :kind="o.items[0].art" :tint="o.items[0].tint" :size="56" bg />
        <div class="nm">
          <p class="ellipsis">{{ o.items.map((i) => i.name).join('、') }}</p>
          <span class="muted">{{ formatTime(o.createdAt) }}</span>
        </div>
        <span class="price">¥{{ yuan(o.price.payAmount, true) }}</span>
        <b :style="{ color: ORDER_STATUS[o.status].color }">{{ ORDER_STATUS[o.status].text }}</b>
      </RouterLink>
      <p v-if="!recent.length" class="muted">暂无订单</p>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 30px;
  background: linear-gradient(110deg, #fff1e6, #fff);
}
.hero h2 {
  font-size: 22px;
}
.hero p {
  font-size: 12px;
  margin-top: 4px;
}
.kpis {
  display: flex;
  gap: 40px;
}
.kpis > * {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  color: var(--c-text-3);
}
.kpis b {
  font-size: 26px;
  color: var(--c-primary);
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 20px 0;
  margin-top: 14px;
}
.st {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.st:hover {
  color: var(--c-primary);
}
.ic {
  position: relative;
  color: var(--c-primary);
}
.ic em {
  position: absolute;
  top: -6px;
  right: -12px;
  font-style: normal;
  background: var(--c-red);
  color: #fff;
  font-size: 11px;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 16px;
}
.block {
  margin-top: 14px;
  padding: 18px 22px;
}
.block h3 {
  display: flex;
  justify-content: space-between;
  font-size: 16px;
  margin-bottom: 10px;
}
.more {
  font-size: 12px;
  font-weight: 400;
  color: var(--c-text-2);
  display: inline-flex;
  align-items: center;
}
.ro {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--c-border);
  font-size: 13px;
}
.nm {
  flex: 1;
  min-width: 0;
}
.nm span {
  font-size: 12px;
}
.ro .price {
  width: 100px;
  text-align: right;
}
.ro b {
  width: 70px;
  text-align: right;
}
</style>
