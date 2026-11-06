<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { adminApi, type OpsLog } from '@/api/admin'
import { formatTime } from '@/utils/format'

const TYPES: Array<[OpsLog['type'] | '', string]> = [['', '全部'], ['ORDER', '订单'], ['PAY', '支付'], ['MQ', '消息队列'], ['CACHE', '缓存'], ['SECKILL', '秒杀'], ['RISK', '风控'], ['REFUND', '售后'], ['ALERT', '告警']]
const type = ref<OpsLog['type'] | ''>('')
const list = ref<OpsLog[]>([])
const auto = ref(true)
async function load() {
  list.value = await adminApi.logs(type.value || undefined)
}
watch(type, load)
let timer = 0
onMounted(() => {
  load()
  timer = window.setInterval(() => auto.value && load(), 2000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="adm-page">
    <div class="adm-title">
      <h2>链路日志</h2><span class="sub">Mock 后端把 MQ 投递、缓存删除、支付回调、Lua 扣减等关键动作记录在这里（最多 100 条）</span>
      <div class="right"><label class="muted chk"><input v-model="auto" type="checkbox" class="checkbox" /> 自动刷新</label></div>
    </div>
    <div class="adm-card">
      <div class="adm-toolbar">
        <button v-for="[k, t] in TYPES" :key="k" class="btn btn-sm btn-square" :class="{ 'btn-ghost': type === k }" @click="type = k">{{ t }}</button>
      </div>
      <div class="console">
        <p v-for="(l, i) in list" :key="i">
          <span class="tm">{{ formatTime(l.time, true).slice(5) }}</span>
          <span class="ty" :class="l.type">{{ l.type.padEnd(7, ' ') }}</span>
          <span>{{ l.text }}</span>
        </p>
        <p v-if="!list.length" class="tm">// 暂无日志</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chk {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.console {
  background: #16181d;
  color: #d6d8de;
  border-radius: 8px;
  padding: 14px 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.9;
  max-height: 620px;
  overflow: auto;
  white-space: pre-wrap;
}
.console p {
  display: flex;
  gap: 12px;
}
.tm {
  color: #7d828f;
  flex: none;
}
.ty {
  flex: none;
  white-space: pre;
  font-weight: 700;
}
.ORDER { color: #8ab4ff; }
.PAY { color: #6fdc9a; }
.MQ { color: #f7c46c; }
.CACHE { color: #7fd6e8; }
.SECKILL { color: #ff8a65; }
.RISK { color: #ff6b81; }
.REFUND { color: #c3a6ff; }
.ALERT { color: #fff; background: #d93025; padding: 0 4px; border-radius: 3px; }
</style>
