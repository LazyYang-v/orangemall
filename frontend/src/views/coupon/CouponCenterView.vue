<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { CouponTemplate } from '@/types/domain'
import { couponApi } from '@/api/marketing'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'
import CouponTicket from '@/components/common/CouponTicket.vue'
import Icon from '@/components/common/Icon.vue'

const list = ref<CouponTemplate[]>([])
const user = useUserStore()
const router = useRouter()
const busy = ref('')
async function load() {
  list.value = await couponApi.templates()
}
onMounted(load)

async function receive(t: CouponTemplate) {
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: '/coupons' } })
  busy.value = t.id
  try {
    await couponApi.receive(t.id)
    toast.success('领取成功，已放入「我的优惠券」')
    load()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <div>
    <div class="banner">
      <div class="container">
        <h1>领券中心</h1>
        <p>大额神券 · 下单立减 · 每日 10 点补货</p>
      </div>
    </div>
    <div class="container page">
      <div class="note"><Icon name="zap" :size="14" /> 高并发领券：Redis Lua 原子校验「剩余量 &gt; 0 且 用户已领 &lt; 限领」后扣减，再异步写库，避免超发</div>
      <div class="grid">
        <CouponTicket
          v-for="t in list"
          :key="t.id"
          :type="t.type"
          :amount="t.amount"
          :threshold="t.threshold"
          :title="t.title"
          :scope="t.scope"
          :progress="{ received: t.received, total: t.total }"
          :dim="t.received >= t.total"
          :stamp="t.receivedByMe ? '已领取' : t.received >= t.total ? '已抢光' : ''"
        >
          <RouterLink v-if="t.receivedByMe" to="/search" class="btn btn-ghost btn-sm">去使用</RouterLink>
          <button v-else class="btn btn-primary btn-sm" :disabled="t.received >= t.total || busy === t.id" @click="receive(t)">立即领取</button>
        </CouponTicket>
      </div>
    </div>
  </div>
</template>

<style scoped>
.banner {
  background: linear-gradient(115deg, #ffb35c, #ff6a1a 50%, #f2301d);
  color: #fff;
  padding: 36px 0;
}
h1 {
  font-size: 40px;
  font-weight: 900;
  font-style: italic;
}
.note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--c-text-3);
  margin-bottom: 14px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
</style>
