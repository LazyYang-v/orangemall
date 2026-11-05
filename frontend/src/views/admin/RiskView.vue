<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { RiskUser } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { toast } from '@/utils/toast'

const list = ref<RiskUser[]>([])
async function load() {
  list.value = await adminApi.risk()
}
onMounted(load)
async function toggle(r: RiskUser) {
  await adminApi.setRisk(r.userId, !r.blocked)
  toast.success(r.blocked ? '已移出黑名单' : '已加入黑名单，秒杀请求将被直接拒绝')
  load()
}
const level = (s: number) => (s < 60 ? { t: '高风险', c: 'tag-red' } : s < 80 ? { t: '关注', c: '' } : { t: '正常', c: 'tag-green' })
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>风控黑名单</h2><span class="sub">信用分 &lt; 60 自动拦截秒杀；命中限流 / 非法路径 / 重复下单会扣分</span></div>
    <div class="adm-card">
      <table class="table">
        <thead><tr><th>用户 ID</th><th>昵称</th><th style="width: 220px">信用分</th><th>风险等级</th><th>命中次数</th><th>最近行为</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="r in list" :key="r.userId">
            <td class="mono">{{ r.userId }}</td>
            <td>{{ r.nickname }}</td>
            <td>
              <div class="score">
                <span class="track"><i :style="{ width: r.creditScore + '%' }" /><em /></span>
                <b class="num">{{ r.creditScore }}</b>
              </div>
            </td>
            <td><span class="tag" :class="level(r.creditScore).c">{{ level(r.creditScore).t }}</span></td>
            <td class="num">{{ r.hits }}</td>
            <td class="muted">{{ r.lastAction }}</td>
            <td><span class="tag" :class="r.blocked ? 'tag-red' : 'tag-gray'">{{ r.blocked ? '已拉黑' : '正常' }}</span></td>
            <td><button class="op" :class="{ danger: !r.blocked }" @click="toggle(r)">{{ r.blocked ? '解除' : '拉黑' }}</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.score {
  display: flex;
  align-items: center;
  gap: 10px;
}
.track {
  position: relative;
  flex: 1;
  height: 6px;
  background: #f1f2f4;
  border-radius: 3px;
}
.track i {
  display: block;
  height: 100%;
  background: #ff6a1a;
  border-radius: 3px;
}
/* 60 分阈值线 */
.track em {
  position: absolute;
  left: 60%;
  top: -3px;
  width: 1px;
  height: 12px;
  background: #555;
}
.score b {
  width: 28px;
  text-align: right;
}
</style>
