<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { PointsGoods, PointsRecord } from '@/types/domain'
import { pointsApi } from '@/api/marketing'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'
import { formatTime } from '@/utils/format'
import ProductArt from '@/components/common/ProductArt.vue'
import Icon from '@/components/common/Icon.vue'

const user = useUserStore()
const router = useRouter()
const goods = ref<PointsGoods[]>([])
const balance = ref(0)
const records = ref<PointsRecord[]>([])

async function load() {
  goods.value = await pointsApi.goods()
  if (user.isLogin) {
    const s = await pointsApi.summary()
    balance.value = s.balance
    records.value = s.records
  }
}
onMounted(load)

async function exchange(g: PointsGoods) {
  if (!user.isLogin) return router.push({ path: '/login', query: { redirect: '/points' } })
  if (!confirm(`确定使用 ${g.points} 积分兑换「${g.name}」？`)) return
  try {
    await pointsApi.exchange(g.id)
    toast.success('兑换成功')
    user.fetchProfile()
    load()
  } catch (e) {
    toast.error((e as Error).message)
  }
}
</script>

<template>
  <div class="container page">
    <section class="head card">
      <div class="bal">
        <Icon name="coins" :size="40" />
        <div>
          <p>我的积分</p>
          <b class="num">{{ user.isLogin ? balance.toLocaleString() : '--' }}</b>
        </div>
      </div>
      <ul class="rules">
        <li><b>购物得积分</b>确认收货后按实付 1 元 = 1 积分发放</li>
        <li><b>积分抵现</b>下单时 100 积分抵 1 元，最多抵扣 20%</li>
        <li><b>积分兑换</b>好物、优惠券随心换，兑完即止</li>
      </ul>
      <RouterLink v-if="!user.isLogin" to="/login?redirect=/points" class="btn btn-primary">登录查看积分</RouterLink>
    </section>

    <h3 class="section-title t">积分兑换</h3>
    <div class="grid">
      <div v-for="g in goods" :key="g.id" class="card good">
        <ProductArt :kind="g.art" :tint="g.tint" bg />
        <p class="ellipsis">{{ g.name }}</p>
        <div class="row">
          <span class="pts num">{{ g.points }}<small> 积分</small></span>
          <button class="btn btn-sm" :class="g.stock > 0 ? 'btn-primary' : ''" :disabled="g.stock <= 0" @click="exchange(g)">{{ g.stock > 0 ? '兑换' : '已兑完' }}</button>
        </div>
        <span class="muted stock">剩余 {{ g.stock }}</span>
      </div>
    </div>

    <template v-if="user.isLogin">
      <h3 class="section-title t">积分明细</h3>
      <div class="card">
        <table class="table">
          <thead><tr><th>时间</th><th>说明</th><th style="text-align: right">变动</th></tr></thead>
          <tbody>
            <tr v-for="r in records" :key="r.id">
              <td>{{ formatTime(r.createdAt, true) }}</td>
              <td>{{ r.reason }}</td>
              <td class="num" :style="{ textAlign: 'right', color: r.change > 0 ? 'var(--c-success)' : 'var(--c-red)' }">{{ r.change > 0 ? '+' : '' }}{{ r.change }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 24px 30px;
  background: linear-gradient(110deg, #fff5e6, #fff);
}
.bal {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #ff9a00;
}
.bal p {
  color: var(--c-text-2);
  font-size: 13px;
}
.bal b {
  font-size: 34px;
  color: var(--c-text);
}
.rules {
  flex: 1;
  display: flex;
  gap: 24px;
  font-size: 12px;
  color: var(--c-text-3);
}
.rules b {
  display: block;
  color: var(--c-text);
  font-size: 14px;
}
.t {
  margin: 24px 0 12px;
  font-size: 18px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}
.good {
  padding: 12px;
}
.good p {
  margin: 8px 0 4px;
  font-size: 13px;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pts {
  color: #ff8a00;
  font-weight: 700;
  font-size: 18px;
}
.pts small {
  font-size: 11px;
}
.stock {
  font-size: 11px;
}
</style>
