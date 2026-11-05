<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import Icon from '../common/Icon.vue'
import CategoryMenu from './CategoryMenu.vue'

/** home=true 时左侧分类常驻展开（由首页渲染），否则悬停弹出 */
defineProps<{ home?: boolean }>()
const route = useRoute()
const hover = ref(false)

const NAV = [
  { to: '/', text: '首页' },
  { to: '/seckill', text: '限时秒杀', icon: 'bolt', color: '#ff5a00' },
  { to: '/coupons', text: '领券中心', icon: 'ticket', color: '#ff4d1a' },
  { to: '/brands', text: '品牌馆', icon: 'diamond', color: '#ff7a1a' },
  { to: '/points', text: '积分商城', icon: 'coins', color: '#ff8a00' },
  { to: '/search?sort=new', text: '新品首发', badge: 'NEW' },
  { to: '/rank', text: '排行榜', icon: 'chart', color: '#ff5a00' },
]

function isActive(to: string) {
  const path = to.split('?')[0]
  if (path === '/') return route.path === '/'
  if (to.includes('sort=new')) return route.path === '/search' && route.query.sort === 'new'
  return route.path.startsWith(path) && !(path === '/search' && route.query.sort !== 'new')
}
</script>

<template>
  <div class="main-nav">
    <div class="container inner">
      <div class="all" @mouseenter="hover = true" @mouseleave="hover = false">
        <div class="all-btn">
          <Icon name="menu" :size="20" :stroke="2" />
          <span>全部商品分类</span>
        </div>
        <CategoryMenu v-if="!home && hover" floating />
      </div>
      <nav>
        <RouterLink v-for="n in NAV" :key="n.to" :to="n.to" class="item" :class="{ on: isActive(n.to) }">
          <Icon v-if="n.icon" :name="n.icon" :size="20" :style="{ color: n.color }" :stroke="2" />
          <span>{{ n.text }}</span>
          <em v-if="n.badge" class="badge">{{ n.badge }}</em>
        </RouterLink>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.main-nav {
  background: #fff;
  border-bottom: 1px solid #f3e6dc;
}
.inner {
  display: flex;
  align-items: flex-end;
  height: 52px;
}
.all {
  position: relative;
  width: 214px;
  flex: none;
  z-index: 30;
}
.all-btn {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 22px;
  background: var(--g-primary);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius) var(--radius) 0 0;
  cursor: pointer;
}
nav {
  display: flex;
  height: 100%;
  margin-left: 30px;
  gap: 46px;
}
.item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 17px;
  font-weight: 500;
  color: var(--c-text);
  padding: 0 4px;
}
.item:hover {
  color: var(--c-primary);
}
.item.on {
  color: var(--c-primary);
  font-weight: 700;
}
.item.on::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--c-primary);
}
.badge {
  position: absolute;
  top: 4px;
  right: -34px;
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: var(--g-primary);
  padding: 1px 6px;
  border-radius: 8px 8px 8px 0;
}
</style>
