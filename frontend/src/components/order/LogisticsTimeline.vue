<script setup lang="ts">
import type { LogisticsInfo } from '@/types/domain'
import { LOGISTICS_STATE } from '@/utils/dict'
import { formatTime } from '@/utils/format'
defineProps<{ info: LogisticsInfo; compact?: boolean }>()
</script>

<template>
  <div class="lg">
    <div class="lg-head">
      <b>{{ info.company }}</b>
      <span class="num muted">运单号 {{ info.trackingNo }}</span>
      <span class="tag" :class="info.state === 'SIGNED' ? 'tag-green' : 'tag-blue'">{{ LOGISTICS_STATE[info.state] }}</span>
    </div>
    <ul>
      <li v-for="(t, i) in compact ? info.traces.slice(0, 3) : info.traces" :key="t.time + i" :class="{ first: i === 0 }">
        <i />
        <div>
          <p>{{ t.desc }}</p>
          <time class="muted">{{ formatTime(t.time, true) }} · {{ t.location }}</time>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.lg-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 13px;
}
ul {
  position: relative;
}
li {
  position: relative;
  display: flex;
  gap: 14px;
  padding-bottom: 16px;
  font-size: 13px;
}
li::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: -2px;
  width: 1px;
  background: var(--c-border-2);
}
li:last-child::before {
  display: none;
}
i {
  flex: none;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #d5d5d5;
  margin-top: 4px;
  position: relative;
}
.first i {
  background: var(--c-primary);
  box-shadow: 0 0 0 4px var(--c-primary-soft);
}
.first p {
  color: var(--c-primary);
  font-weight: 600;
}
time {
  font-size: 12px;
}
</style>
