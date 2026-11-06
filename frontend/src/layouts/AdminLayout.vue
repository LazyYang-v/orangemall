<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { adminApi } from '@/api/admin'
import { USE_MOCK } from '@/api/http'
import { toast } from '@/utils/toast'
import Logo from '@/components/layout/Logo.vue'
import Icon from '@/components/common/Icon.vue'
import '@/styles/admin.css'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const MENU: Array<{ group: string; items: Array<{ to: string; icon: string; text: string; exact?: boolean }> }> = [
  { group: '概览', items: [{ to: '/admin', icon: 'chart', text: '数据看板', exact: true }] },
  { group: '交易', items: [{ to: '/admin/orders', icon: 'order', text: '订单管理' }, { to: '/admin/refunds', icon: 'refund', text: '售后管理' }] },
  {
    group: '用户',
    items: [
      { to: '/admin/users', icon: 'user', text: '用户管理' },
      { to: '/admin/risk', icon: 'shield', text: '风控黑名单' },
    ],
  },
  {
    group: '商品',
    items: [
      { to: '/admin/products', icon: 'package', text: '商品 / SKU' },
      { to: '/admin/categories', icon: 'layers', text: '类目树' },
    ],
  },
  {
    group: '营销',
    items: [
      { to: '/admin/coupons', icon: 'coupon', text: '优惠券模板' },
      { to: '/admin/seckill', icon: 'bolt', text: '秒杀活动' },
    ],
  },
  {
    group: '稳定性',
    items: [
      { to: '/admin/cache', icon: 'database', text: '缓存一致性' },
      { to: '/admin/logs', icon: 'activity', text: '链路日志' },
    ],
  },
]
async function reset() {
  if (!confirm('重置全部 Mock 数据（订单、库存、优惠券、日志）？')) return
  await adminApi.resetMock()
  toast.success('Mock 数据已重置，请重新登录')
  user.clear()
  router.push('/login')
}
</script>

<template>
  <div class="admin">
    <aside>
      <div class="brand"><Logo small /></div>
      <p class="env">运营后台 · {{ USE_MOCK ? 'Mock 模式' : '网关模式' }}</p>
      <nav>
        <template v-for="g in MENU" :key="g.group">
          <p class="grp">{{ g.group }}</p>
          <RouterLink
            v-for="m in g.items" :key="m.to" :to="m.to"
            :class="{ on: m.exact ? route.path === m.to : route.path.startsWith(m.to) }"
          ><Icon :name="m.icon" :size="16" /> {{ m.text }}</RouterLink>
        </template>
      </nav>
    </aside>
    <section class="main">
      <header>
        <span class="crumb">{{ route.meta.title }}</span>
        <div class="right">
          <RouterLink to="/" class="lnk"><Icon name="store" :size="15" /> 返回商城</RouterLink>
          <button v-if="USE_MOCK" class="lnk" @click="reset"><Icon name="refresh" :size="15" /> 重置 Mock</button>
          <span class="me"><Icon name="user" :size="15" /> {{ user.profile?.nickname }}</span>
        </div>
      </header>
      <RouterView />
    </section>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: #f3f4f7;
}
aside {
  width: 216px;
  flex: none;
  background: #1d1f26;
  color: #c9cbd3;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}
.brand {
  background: #fff;
  margin: 12px;
  border-radius: 10px;
  padding: 8px 10px;
}
.env {
  font-size: 12px;
  color: #8a8d99;
  padding: 0 18px 10px;
}
.grp {
  font-size: 11px;
  color: #6c6f7b;
  padding: 14px 20px 6px;
  letter-spacing: 1px;
}
nav a {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 20px;
  font-size: 13px;
  border-left: 3px solid transparent;
}
nav a:hover {
  color: #fff;
}
nav a.on {
  color: #fff;
  background: rgba(255, 90, 0, 0.16);
  border-left-color: var(--c-primary);
}
.main {
  flex: 1;
  min-width: 0;
}
header {
  height: 52px;
  background: #fff;
  border-bottom: 1px solid #e8e8ec;
  display: flex;
  align-items: center;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.crumb {
  font-weight: 600;
}
.right {
  margin-left: auto;
  display: flex;
  gap: 18px;
  align-items: center;
  font-size: 13px;
}
.lnk {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: var(--c-text-2);
  font-size: 13px;
  padding: 0;
}
.lnk:hover {
  color: var(--c-primary);
}
.me {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--c-text-3);
}

/* ≤900px：侧栏变顶部菜单条（单行横滑，当前项自动可见），面包屑栏紧凑化 */
@media (max-width: 900px) {
  .admin {
    flex-direction: column;
  }
  aside {
    width: auto;
    height: auto;
    position: sticky;
    z-index: 20;
    overflow: visible;
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 8px;
  }
  .brand {
    margin: 0;
    padding: 4px 6px;
    flex: none;
  }
  .env,
  .grp {
    display: none;
  }
  nav {
    display: flex;
    overflow-x: auto;
    scrollbar-width: none;
    min-width: 0;
  }
  nav::-webkit-scrollbar {
    display: none;
  }
  nav a {
    flex: none;
    padding: 8px 10px;
    gap: 6px;
    border-left: none;
    border-bottom: 2px solid transparent;
    white-space: nowrap;
  }
  nav a.on {
    border-bottom-color: var(--c-primary);
  }
  header {
    top: 46px;
    height: 44px;
    padding: 0 12px;
  }
  .right {
    gap: 12px;
  }
  .me {
    display: none;
  }
}
</style>
