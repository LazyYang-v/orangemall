<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { Notice } from '@/types/domain'
import { useUserStore } from '@/stores/user'
import { orderApi } from '@/api/order'
import Mascot from '../common/Mascot.vue'
import Icon from '../common/Icon.vue'
import Modal from '../common/Modal.vue'
import { shortDate } from '@/utils/format'

defineProps<{ notices: Notice[] }>()
const user = useUserStore()
const counts = ref<Record<string, number>>({})
const reading = ref<Notice | null>(null)

async function loadCounts() {
  if (!user.isLogin) return (counts.value = {})
  try {
    counts.value = await orderApi.counts()
  } catch {
    counts.value = {}
  }
}
onMounted(loadCounts)
watch(() => user.isLogin, loadCounts)

const SHORTCUTS = [
  { icon: 'order', text: '我的订单', to: '/orders', badge: 'PENDING_PAYMENT' },
  { icon: 'coupon', text: '我的优惠券', to: '/user/coupons' },
  { icon: 'dollar', text: '我的积分', to: '/points' },
  { icon: 'service', text: '售后服务', to: '/orders?status=COMPLETED' },
]
</script>

<template>
  <aside class="panel">
    <div class="hello">
      <Mascot :size="58" />
      <div class="txt">
        <template v-if="user.isLogin && user.profile">
          <b class="ellipsis">Hi, {{ user.profile.nickname }}</b>
          <p>Lv{{ user.profile.level }} 会员 · 积分 <em class="num">{{ user.profile.points }}</em></p>
        </template>
        <template v-else>
          <b>Hi, 欢迎来到橙选商城</b>
          <p>登录后享受更多专属服务</p>
        </template>
      </div>
    </div>
    <div class="btns">
      <template v-if="user.isLogin">
        <RouterLink to="/user" class="btn btn-primary btn-square btn-sm">个人中心</RouterLink>
        <RouterLink to="/coupons" class="btn btn-ghost btn-square btn-sm">领券中心</RouterLink>
      </template>
      <template v-else>
        <RouterLink to="/login" class="btn btn-primary btn-square btn-sm">立即登录</RouterLink>
        <RouterLink to="/register" class="btn btn-ghost btn-square btn-sm">免费注册</RouterLink>
      </template>
    </div>
    <div class="shortcuts">
      <RouterLink v-for="s in SHORTCUTS" :key="s.text" :to="s.to" class="sc">
        <span class="ic">
          <Icon :name="s.icon" :size="24" :stroke="1.6" />
          <em v-if="s.badge && counts[s.badge]">{{ counts[s.badge] }}</em>
        </span>
        <span>{{ s.text }}</span>
      </RouterLink>
    </div>
    <div class="notice">
      <div class="nh">
        <b>商城公告</b>
        <a class="more" @click="reading = notices[0] ?? null">更多 <Icon name="chevron-right" :size="12" /></a>
      </div>
      <ul>
        <li v-for="n in notices.slice(0, 3)" :key="n.id" @click="reading = n">
          <span class="ellipsis">· {{ n.title }}</span>
          <time class="num">{{ shortDate(n.date) }}</time>
        </li>
      </ul>
    </div>
    <Modal :open="!!reading" title="商城公告" @close="reading = null">
      <ul class="notice-all">
        <li v-for="n in notices" :key="n.id" :class="{ on: reading?.id === n.id }" @click="reading = n">
          <b>{{ n.title }}</b>
          <time class="muted">{{ n.date }}</time>
          <p v-if="reading?.id === n.id">{{ n.content }}</p>
        </li>
      </ul>
    </Modal>
  </aside>
</template>

<style scoped>
.panel {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 16px 18px 12px;
  display: flex;
  flex-direction: column;
}
.hello {
  display: flex;
  align-items: center;
  gap: 12px;
}
.txt {
  min-width: 0;
}
.txt b {
  display: block;
  font-size: 16px;
}
.txt p {
  font-size: 12px;
  color: var(--c-text-3);
  margin-top: 2px;
}
.txt em {
  color: var(--c-primary);
  font-style: normal;
  font-weight: 700;
}
.btns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 14px 0 12px;
}
.btns .btn {
  height: 32px;
  font-size: 13px;
}
.shortcuts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding-bottom: 10px;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--c-text);
}
.sc:hover {
  color: var(--c-primary);
}
.ic {
  position: relative;
}
.ic em {
  position: absolute;
  top: -6px;
  right: -10px;
  background: var(--c-red);
  color: #fff;
  font-style: normal;
  font-size: 10px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  padding: 0 4px;
}
.notice {
  border-top: 1px solid var(--c-border);
  padding-top: 10px;
}
.nh {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.nh b {
  color: var(--c-primary);
  font-size: 15px;
}
.more {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: var(--c-text-2);
  cursor: pointer;
}
.notice li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12.5px;
  line-height: 24px;
  cursor: pointer;
  color: var(--c-text-2);
}
.notice li:hover {
  color: var(--c-primary);
}
time {
  color: var(--c-text-3);
  flex: none;
}
.notice-all li {
  display: block;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);
  line-height: 1.7;
  font-size: 14px;
}
.notice-all li.on b {
  color: var(--c-primary);
}
.notice-all time {
  margin-left: 10px;
  font-size: 12px;
}
.notice-all p {
  color: var(--c-text-2);
  margin-top: 4px;
}
</style>
