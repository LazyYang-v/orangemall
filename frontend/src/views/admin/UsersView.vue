<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { AdminUser } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { formatTime, maskPhone } from '@/utils/format'
import Modal from '@/components/common/Modal.vue'
import Pagination from '@/components/common/Pagination.vue'

const q = reactive({ keyword: '', page: 1, size: 10 })
const list = ref<AdminUser[]>([])
const total = ref(0)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const r = await adminApi.users(q)
    list.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
function search() {
  q.page = 1
  load()
}

const detail = ref<AdminUser | null>(null)
async function view(u: AdminUser) {
  try {
    detail.value = await adminApi.userDetail(u.id)
  } catch {
    detail.value = u
  }
}
async function setBlock(u: AdminUser, blocked: boolean) {
  if (blocked && !confirm(`封禁用户 ${u.nickname}（${maskPhone(u.phone)}）？封禁后无法下单/支付（风控拦截）。`)) return
  try {
    await adminApi.setRisk(u.id, blocked)
    toast.success(blocked ? '已封禁，订单提交将被风控拦截' : '已解除封禁')
    load()
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
</script>

<template>
  <div class="adm-page">
    <div class="adm-title"><h2>用户管理</h2><span class="sub">注册用户 · 风控状态 · 封禁复用 risk 黑名单</span></div>
    <div class="adm-card">
      <div class="adm-toolbar">
        <input v-model.trim="q.keyword" class="input" style="width: 240px" placeholder="手机号 / 昵称" @keyup.enter="search" />
        <button class="btn btn-primary btn-sm btn-square" @click="search">查询</button>
      </div>
      <table class="table">
        <thead>
          <tr><th>用户 ID</th><th>昵称</th><th>手机号</th><th>积分</th><th>信用分</th><th>风控标记</th><th>注册时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="u in list" :key="u.id">
            <td class="mono">{{ u.id }}</td>
            <td>{{ u.nickname }}</td>
            <td class="num">{{ maskPhone(u.phone) }}</td>
            <td class="num">{{ u.points }}</td>
            <td class="num">{{ u.creditScore }}</td>
            <td>
              <span class="tag" :class="u.blocked ? 'tag-red' : 'tag-gray'">{{ u.blocked ? '已封禁' : '正常' }}</span>
              <span v-if="u.riskHits" class="muted num"> ×{{ u.riskHits }}</span>
            </td>
            <td class="num muted">{{ formatTime(u.createdAt) }}</td>
            <td>
              <button class="op" @click="view(u)">详情</button>
              <button v-if="!u.blocked" class="op danger" @click="setBlock(u, true)">封禁</button>
              <button v-else class="op" @click="setBlock(u, false)">解封</button>
            </td>
          </tr>
          <tr v-if="!list.length && !loading"><td colspan="8" class="muted" style="text-align: center; padding: 40px">暂无用户</td></tr>
        </tbody>
      </table>
      <Pagination :page="q.page" :size="q.size" :total="total" @change="(p) => { q.page = p; load() }" />
    </div>

    <Modal :open="!!detail" title="用户详情" @close="detail = null">
      <template v-if="detail">
        <dl class="ud">
          <div><dt>用户 ID</dt><dd class="mono">{{ detail.id }}</dd></div>
          <div><dt>昵称</dt><dd>{{ detail.nickname }}</dd></div>
          <div><dt>手机号</dt><dd class="num">{{ detail.phone }}</dd></div>
          <div><dt>等级 / 积分</dt><dd class="num">Lv.{{ detail.level ?? 1 }} · {{ detail.points }}</dd></div>
          <div><dt>信用分</dt><dd class="num">{{ detail.creditScore }}</dd></div>
          <div v-if="detail.lastAction"><dt>最近动作</dt><dd class="mono ellipsis" style="max-width: 260px">{{ detail.lastAction }}</dd></div>
          <div><dt>状态</dt><dd><span class="tag" :class="detail.blocked ? 'tag-red' : 'tag-gray'">{{ detail.blocked ? '已封禁' : '正常' }}</span></dd></div>
          <div><dt>注册时间</dt><dd class="num">{{ formatTime(detail.createdAt) }}</dd></div>
        </dl>
      </template>
      <template #footer>
        <button class="btn" @click="detail = null">关闭</button>
        <button v-if="detail && !detail.blocked" class="btn btn-primary" @click="setBlock(detail, true); detail = null">封禁该用户</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.ud {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
}
.ud dt {
  font-size: 12px;
  color: #8a8d99;
  margin-bottom: 2px;
}
.ud dd {
  font-size: 13px;
}
.op {
  background: none;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
  margin-right: 4px;
}
.op.danger {
  color: #e02020;
  border-color: #ffd7d7;
}
</style>
