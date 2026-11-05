<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import Icon from '../common/Icon.vue'
import { toast } from '@/utils/toast'

const user = useUserStore()
const router = useRouter()

async function logout() {
  await user.logout()
  toast.success('已退出登录')
  router.push('/')
}
</script>

<template>
  <div class="topbar">
    <div class="container inner">
      <div class="left">
        <span class="loc">送至：陕西汉中 <Icon name="chevron-down" :size="12" /></span>
        <template v-if="user.isLogin">
          <span class="hi">你好，<RouterLink to="/user">{{ user.profile?.nickname ?? '...' }}</RouterLink></span>
          <a class="link" @click="logout">退出</a>
        </template>
        <template v-else>
          <RouterLink to="/login" class="hl">你好，请登录</RouterLink>
          <RouterLink to="/register" class="hl">免费注册</RouterLink>
        </template>
      </div>
      <nav class="right">
        <RouterLink to="/orders">我的订单</RouterLink>
        <i />
        <RouterLink to="/user/favorites">我的收藏</RouterLink>
        <i />
        <RouterLink to="/user/coupons">我的优惠券</RouterLink>
        <i />
        <a>客服中心</a>
        <i />
        <RouterLink v-if="user.isAdmin" to="/admin" class="hl">运营后台</RouterLink>
        <a v-else>商家入驻</a>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.topbar {
  height: 36px;
  background: #f2f2f2;
  font-size: 13px;
  color: var(--c-text-2);
  border-bottom: 1px solid #ebebeb;
}
.inner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.left,
.right {
  display: flex;
  align-items: center;
  gap: 22px;
}
.right {
  gap: 14px;
}
.loc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.hl {
  color: var(--c-primary);
}
.hi a {
  color: var(--c-primary);
}
.link {
  cursor: pointer;
}
.right a:hover,
.link:hover {
  color: var(--c-primary);
}
i {
  width: 1px;
  height: 12px;
  background: #d0d0d0;
}
</style>
