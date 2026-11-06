<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { adminApi, type SeckillDeadLetter, type SeckillHealth } from '@/api/admin'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { formatTime } from '@/utils/format'
import Modal from '@/components/common/Modal.vue'

/** OM-013 在途滞留 / OM-019 死信：健康视图 + 处置入口 */
const health = ref<SeckillHealth | null>(null)
const letters = ref<SeckillDeadLetter[]>([])
const dlStatus = ref<'NEW' | 'RESOLVED' | ''>('NEW')
const busy = ref('')
/** 无强制时被 7010 拒绝的活动：展示「强制重置」确认 */
const forceAsk = ref<{ aid: string; msg: string } | null>(null)
const resolving = ref<SeckillDeadLetter | null>(null)
const remark = ref('')

const STATE: Record<string, [string, string]> = {
  NEW: ['观察中', 'tag-gray'],
  BACKLOG: ['消费积压', ''],
  UNKNOWN: ['探测失败', ''],
  LEAK: ['计数泄漏', 'tag-red'],
}
const REASON: Record<string, string> = {
  POISON: '无法解析',
  INVALID: '字段非法',
  EXHAUSTED: '重试耗尽(已补偿)',
  COMPENSATE_FAILED: '补偿失败',
}

const lag = computed(() => Number(health.value?.lag ?? 0))
const dlNew = computed(() => Number(health.value?.deadLetters.new ?? 0))

async function load() {
  const [h, d] = await Promise.all([adminApi.seckillHealth(), adminApi.deadLetters(dlStatus.value || undefined)])
  health.value = h
  letters.value = d
}
watch(dlStatus, load)
let timer = 0
onMounted(() => {
  load()
  timer = window.setInterval(load, 3000)
})
onUnmounted(() => clearInterval(timer))

async function reset(aid: string, force: boolean) {
  busy.value = aid
  try {
    const r = await adminApi.inflightReset(aid, force)
    forceAsk.value = null
    toast.success(r.reset ? `已清除滞留在途 ${r.cleared}，Redis 库存回补为 ${r.redisStock}` : '当前无在途，无需重置')
    await load()
  } catch (e) {
    const err = e as ApiError
    if (err.code === 7010) forceAsk.value = { aid, msg: err.message }
    else toast.error(err.message)
  } finally {
    busy.value = ''
  }
}

async function toggleConsumer() {
  const pause = !health.value?.consumer.paused
  busy.value = 'consumer'
  try {
    await adminApi.seckillConsumer(pause ? 'pause' : 'resume')
    toast.success(pause ? '已暂停秒杀主消费者（演练）' : '已恢复秒杀主消费者')
    await load()
  } finally {
    busy.value = ''
  }
}

function openResolve(d: SeckillDeadLetter) {
  resolving.value = d
  remark.value = ''
}
async function submitResolve() {
  if (!resolving.value) return
  try {
    await adminApi.resolveDeadLetter(resolving.value.id, remark.value)
    toast.success(`死信 #${resolving.value.id} 已标记处理`)
    resolving.value = null
    await load()
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
</script>

<template>
  <div class="adm-card rel">
    <h3>
      可靠性 <span class="hint">在途滞留 ≥ {{ health?.stuckSeconds ?? 180 }}s 告警；无积压时{{ health?.autoReset ? '自动' : '需手动' }}重置；死信只留档不重放</span>
    </h3>
    <div class="strip">
      <div class="cell">
        <span class="k">主消费者</span>
        <span class="v">
          <span class="tag" :class="health?.consumer.paused ? 'tag-red' : health?.consumer.running ? 'tag-green' : 'tag-gray'">
            {{ health?.consumer.paused ? '已暂停' : health?.consumer.running ? '运行中' : '未运行' }}
          </span>
          <button class="op" :class="{ danger: !health?.consumer.paused }" :disabled="!health || busy === 'consumer'" @click="toggleConsumer">
            {{ health?.consumer.paused ? '恢复' : '暂停（演练）' }}
          </button>
        </span>
      </div>
      <div class="cell">
        <span class="k">Kafka 积压</span>
        <span class="v num" :class="{ warn: lag > 0 }">{{ lag < 0 ? '探测失败' : lag.toLocaleString() + ' 条' }}</span>
      </div>
      <div class="cell">
        <span class="k">滞留活动</span>
        <span class="v num" :class="{ warn: health?.inflight.some((r) => r.state !== 'NEW') }">{{ health?.inflight.length ?? 0 }}</span>
      </div>
      <div class="cell">
        <span class="k">待处理死信</span>
        <span class="v num" :class="{ bad: dlNew > 0 }">{{ dlNew }} <small class="muted">/ 累计 {{ health?.deadLetters.total ?? 0 }}</small></span>
      </div>
    </div>

    <table v-if="health?.inflight.length" class="table">
      <thead><tr><th>活动 ID</th><th>场次</th><th>在途</th><th>未变化</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="r in health.inflight" :key="r.activityId">
          <td class="mono">{{ r.activityId }}</td>
          <td>{{ r.session }}</td>
          <td class="num">{{ r.inflight }}</td>
          <td class="num">{{ r.heldSeconds }}s</td>
          <td><span class="tag" :class="STATE[r.state]?.[1]">{{ STATE[r.state]?.[0] ?? r.state }}</span></td>
          <td>
            <template v-if="forceAsk?.aid === r.activityId">
              <span class="ask">{{ forceAsk.msg }}</span>
              <button class="op danger" :disabled="busy === r.activityId" @click="reset(r.activityId, true)">强制重置</button>
              <button class="op" @click="forceAsk = null">取消</button>
            </template>
            <button v-else class="op" :disabled="busy === r.activityId" @click="reset(r.activityId, false)">重置在途</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="dl-head">
      <b>死信</b>
      <button v-for="[k, t] in [['NEW', '待处理'], ['RESOLVED', '已处理'], ['', '全部']] as const" :key="k" class="btn btn-sm btn-square" :class="{ 'btn-ghost': dlStatus === k }" @click="dlStatus = k">{{ t }}</button>
    </div>
    <div class="scroll">
      <table class="table">
        <thead><tr><th>#</th><th>时间</th><th>原因</th><th>活动 / 用户</th><th>错误</th><th>消息体</th><th>源位点</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="d in letters" :key="d.id">
            <td class="num">{{ d.id }}</td>
            <td class="nowrap">{{ formatTime(d.createdAt, true).slice(5) }}</td>
            <td><span class="tag" :class="d.reason === 'COMPENSATE_FAILED' ? 'tag-red' : ''">{{ REASON[d.reason] ?? d.reason }}</span></td>
            <td class="mono small">{{ d.activityId || '—' }}<br />{{ d.userId || '—' }}</td>
            <td class="err" :title="d.error ?? ''">{{ d.error || '—' }}</td>
            <td class="mono payload" :title="d.payload">{{ d.payload }}</td>
            <td class="mono">{{ d.srcPartition ?? '—' }}-{{ d.srcOffset ?? '—' }}</td>
            <td>
              <span class="tag" :class="d.status === 'NEW' ? 'tag-red' : 'tag-green'">{{ d.status === 'NEW' ? '待处理' : '已处理' }}</span>
              <div v-if="d.remark" class="muted small" :title="d.remark">{{ d.remark }}</div>
            </td>
            <td><button v-if="d.status === 'NEW'" class="op" @click="openResolve(d)">标记处理</button></td>
          </tr>
          <tr v-if="!letters.length"><td colspan="9" class="muted empty">暂无死信</td></tr>
        </tbody>
      </table>
    </div>

    <Modal :open="!!resolving" title="标记死信已处理" @close="resolving = null">
      <p class="muted" style="margin-bottom: 10px">
        死信 #{{ resolving?.id }} · {{ resolving && (REASON[resolving.reason] ?? resolving.reason) }}
        <template v-if="resolving?.reason === 'EXHAUSTED'"> · 已自动回补库存与限购，如需为用户补单请走人工流程</template>
      </p>
      <div class="field">
        <label>处理备注</label>
        <input v-model.trim="remark" class="input" maxlength="200" placeholder="如：核实为演练注入消息，无需补单" />
      </div>
      <template #footer>
        <button class="btn" @click="resolving = null">取消</button>
        <button class="btn btn-primary" @click="submitResolve">确认处理</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.cell {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cell .k {
  font-size: 12px;
  color: var(--c-text-3);
}
.cell .v {
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.cell .v .op {
  margin: 0;
}
.warn {
  color: #d97706;
}
.bad {
  color: var(--c-red);
  font-weight: 700;
}
.ask {
  font-size: 12px;
  color: #d97706;
  margin-right: 8px;
}
.dl-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 8px;
}
.dl-head b {
  font-size: 14px;
  margin-right: 4px;
}
.scroll {
  overflow-x: auto;
  max-height: 360px;
  overflow-y: auto;
}
.err {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.payload {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.small {
  font-size: 12px;
}
.nowrap {
  white-space: nowrap;
}
.empty {
  text-align: center;
  padding: 18px 0;
}
@media (max-width: 900px) {
  .strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
