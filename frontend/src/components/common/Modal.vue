<script setup lang="ts">
import Icon from './Icon.vue'
withDefaults(defineProps<{ open: boolean; title?: string; width?: number }>(), { width: 480 })
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="mask" @click.self="emit('close')">
        <div class="modal" :style="{ width: width + 'px' }" role="dialog" aria-modal="true">
          <header v-if="title">
            <h3>{{ title }}</h3>
            <button class="x" aria-label="关闭" @click="emit('close')"><Icon name="close" /></button>
          </header>
          <div class="content"><slot /></div>
          <footer v-if="$slots.footer"><slot name="footer" /></footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  animation: pop 0.2s ease;
}
@keyframes pop {
  from { transform: scale(0.96); opacity: 0; }
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--c-border);
}
h3 {
  font-size: 16px;
}
.x {
  border: none;
  background: none;
  color: var(--c-text-3);
}
.content {
  padding: 20px;
  overflow: auto;
}
footer {
  padding: 12px 20px 18px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
