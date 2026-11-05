<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Logo from './Logo.vue'
import Icon from '../common/Icon.vue'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'

withDefaults(defineProps<{ hotKeywords?: string[] }>(), {
  hotKeywords: () => ['iPhone17', '联想笔记本', '运动鞋', '羽绒服', '咖啡机', '双11'],
})
const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const user = useUserStore()
const keyword = ref((route.query.keyword as string) ?? '')
watch(() => route.query.keyword, (v) => (keyword.value = (v as string) ?? ''))

function search(kw = keyword.value) {
  const k = kw.trim()
  router.push({ path: '/search', query: k ? { keyword: k } : {} })
}
</script>

<template>
  <header class="site-header">
    <div class="container inner">
      <div class="brand">
        <Logo />
        <span class="slogan">好物精选 · 低价放心</span>
      </div>
      <div class="search-area">
        <form class="search" role="search" @submit.prevent="search()">
          <Icon name="search" :size="20" class="s-ic" />
          <input v-model="keyword" placeholder="搜索商品、品牌、店铺" aria-label="搜索" />
          <button type="button" class="cam" title="拍照购（待接入）" @click="toast.info('拍照购功能即将上线')"><Icon name="camera" :size="22" /></button>
          <button type="submit" class="go">搜索</button>
        </form>
        <div class="hot">
          <span>热搜：</span>
          <a v-for="(k, i) in hotKeywords" :key="k" :class="{ red: i === hotKeywords.length - 1 }" @click="search(k)">{{ k }}</a>
        </div>
      </div>
      <div class="shortcuts">
        <RouterLink :to="user.isLogin ? '/user' : '/login'" class="sc">
          <Icon name="user" :size="28" :stroke="1.6" />
          <span>{{ user.isLogin ? '个人中心' : '登录 / 注册' }}</span>
        </RouterLink>
        <RouterLink to="/orders" class="sc">
          <Icon name="order" :size="28" :stroke="1.6" />
          <span>我的订单</span>
        </RouterLink>
        <RouterLink to="/cart" class="sc">
          <span class="cart-ic">
            <Icon name="cart" :size="30" :stroke="1.6" />
            <em v-if="cart.count" class="num">{{ cart.count > 99 ? '99+' : cart.count }}</em>
          </span>
          <span>购物车</span>
        </RouterLink>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  background: #fff;
}
.inner {
  display: flex;
  align-items: center;
  height: 110px;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 18px;
}
.slogan {
  color: var(--c-text-2);
  font-size: 16px;
  padding-left: 18px;
  border-left: 1px solid var(--c-border-2);
  white-space: nowrap;
}
.search-area {
  flex: 1;
  padding-top: 18px;
}
.search {
  display: flex;
  align-items: center;
  height: 46px;
  border: 2px solid var(--c-primary);
  border-radius: 999px;
  overflow: hidden;
  background: #fff;
}
.s-ic {
  margin: 0 8px 0 18px;
  color: var(--c-text-3);
}
.search input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  height: 100%;
}
.cam {
  border: none;
  background: none;
  color: var(--c-text-3);
  padding: 0 14px;
}
.cam:hover {
  color: var(--c-primary);
}
.go {
  height: 100%;
  width: 104px;
  border: none;
  background: var(--g-primary);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
}
.go:hover {
  filter: brightness(1.06);
}
.hot {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--c-text-2);
  padding: 6px 0 0 8px;
}
.hot a {
  cursor: pointer;
}
.hot a:hover {
  color: var(--c-primary);
}
.hot .red {
  color: var(--c-red);
}
.shortcuts {
  display: flex;
  gap: 34px;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text);
}
.sc:hover {
  color: var(--c-primary);
}
.cart-ic {
  position: relative;
}
.cart-ic em {
  position: absolute;
  top: -8px;
  right: -10px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--c-red);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 18px;
  text-align: center;
  border: 1.5px solid #fff;
}
</style>
