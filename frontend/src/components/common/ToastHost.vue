<script setup lang="ts">
import { toastState } from '@/utils/toast'
import Icon from './Icon.vue'
const ICON = { info: 'bell', success: 'check-circle', error: 'alert', warning: 'alert' } as const
</script>

<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <div v-for="t in toastState.list" :key="t.id" class="toast" :class="t.type">
        <Icon :name="ICON[t.type]" :size="16" />
        <span>{{ t.text }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}
.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 999px;
  background: rgba(30, 30, 30, 0.9);
  color: #fff;
  font-size: 14px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}
.toast.success { background: rgba(18, 161, 80, 0.95); }
.toast.error { background: rgba(230, 40, 40, 0.95); }
.toast.warning { background: rgba(240, 140, 0, 0.95); }
.toast-enter-active, .toast-leave-active { transition: all 0.25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
