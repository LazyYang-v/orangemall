<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import Mascot from '@/components/common/Mascot.vue'
import Icon from '@/components/common/Icon.vue'
const user = useUserStore()
const MENU = [
  { to: '/user', icon: 'home', text: '账户总览', exact: true },
  { to: '/orders', icon: 'order', text: '我的订单' },
  { to: '/user/coupons', icon: 'coupon', text: '我的优惠券' },
  { to: '/points', icon: 'coins', text: '我的积分' },
  { to: '/user/favorites', icon: 'heart', text: '我的收藏' },
  { to: '/user/addresses', icon: 'location', text: '收货地址' },
]
</script>

<template>
  <div class="container page uc">
    <aside class="card side">
      <div class="me">
        <Mascot :size="64" />
        <b>{{ user.profile?.nickname }}</b>
        <span class="tag tag-solid">Lv{{ user.profile?.level }} 会员</span>
      </div>
      <nav>
        <RouterLink v-for="m in MENU" :key="m.to" :to="m.to" :class="{ exact: m.exact }"><Icon :name="m.icon" :size="16" /> {{ m.text }}</RouterLink>
      </nav>
    </aside>
    <div class="body"><RouterView /></div>
  </div>
</template>

<style scoped>
.uc {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.side {
  width: 200px;
  flex: none;
  padding: 20px 0;
}
.me {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--c-border);
}
nav {
  padding-top: 8px;
}
nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 28px;
  color: var(--c-text-2);
}
nav a:hover {
  color: var(--c-primary);
}
nav a.router-link-exact-active,
nav a:not(.exact).router-link-active {
  color: var(--c-primary);
  background: var(--c-primary-soft);
  border-right: 3px solid var(--c-primary);
  font-weight: 600;
}
.body {
  flex: 1;
  min-width: 0;
}
</style>
