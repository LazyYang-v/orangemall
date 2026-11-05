<script setup lang="ts">
import { yuan, formatTime, percent } from '@/utils/format'
import { COUPON_TYPE } from '@/utils/dict'
import type { CouponType } from '@/types/domain'

defineProps<{
  type: CouponType
  amount: number
  threshold: number
  title: string
  scope: string
  validTo?: string
  progress?: { received: number; total: number }
  dim?: boolean
  stamp?: string
}>()
</script>

<template>
  <div class="ticket" :class="{ dim }">
    <div class="l">
      <div class="amt">
        <template v-if="type === 'DISCOUNT'"><b>{{ amount / 10 }}</b><small>折</small></template>
        <template v-else><small>¥</small><b>{{ yuan(amount) }}</b></template>
      </div>
      <span>{{ threshold ? `满${yuan(threshold)}可用` : '无门槛' }}</span>
    </div>
    <div class="r">
      <p class="t ellipsis"><span class="tag">{{ COUPON_TYPE[type] }}</span> {{ title }}</p>
      <p class="s ellipsis">{{ scope }}</p>
      <p v-if="validTo" class="s">有效期至 {{ formatTime(validTo) }}</p>
      <div v-if="progress" class="prog">
        <div class="track"><i :style="{ width: percent(progress.received, progress.total) + '%' }" /></div>
        <span>已抢 {{ percent(progress.received, progress.total) }}%</span>
      </div>
      <div class="act"><slot /></div>
    </div>
    <span v-if="stamp" class="stamp">{{ stamp }}</span>
  </div>
</template>

<style scoped>
.ticket {
  position: relative;
  display: flex;
  background: #fff;
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  border: 1px solid #ffe2d1;
}
.l {
  width: 120px;
  flex: none;
  background: var(--g-primary);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  position: relative;
}
.l::after {
  content: '';
  position: absolute;
  right: -1px;
  top: 6px;
  bottom: 6px;
  border-right: 2px dotted #fff;
}
.amt b {
  font-size: 36px;
  font-family: var(--font-num);
  line-height: 1;
}
.amt small {
  font-size: 16px;
}
.l span {
  font-size: 12px;
}
.r {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.t {
  font-weight: 600;
}
.s {
  font-size: 12px;
  color: var(--c-text-3);
}
.prog {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--c-primary);
}
.track {
  width: 100px;
  height: 5px;
  background: #ffe1d6;
  border-radius: 3px;
  overflow: hidden;
}
.track i {
  display: block;
  height: 100%;
  background: var(--c-primary);
}
.act {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
}
.dim .l {
  background: #c8c8c8;
}
.dim {
  border-color: var(--c-border);
}
.stamp {
  position: absolute;
  right: 12px;
  top: 10px;
  border: 2px solid #ccc;
  color: #bbb;
  border-radius: 50%;
  width: 58px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  transform: rotate(-20deg);
}
</style>
