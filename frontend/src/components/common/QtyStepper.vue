<script setup lang="ts">
import Icon from './Icon.vue'
const props = withDefaults(defineProps<{ modelValue: number; min?: number; max?: number; disabled?: boolean; small?: boolean }>(), { min: 1, max: 99 })
const emit = defineEmits<{ 'update:modelValue': [number] }>()

function set(v: number) {
  const n = Math.max(props.min, Math.min(props.max, Math.floor(v) || props.min))
  if (n !== props.modelValue) emit('update:modelValue', n)
}
</script>

<template>
  <div class="qty" :class="{ small, disabled }">
    <button :disabled="disabled || modelValue <= min" @click="set(modelValue - 1)"><Icon name="minus" :size="12" :stroke="2.4" /></button>
    <input :value="modelValue" :disabled="disabled" @change="set(Number(($event.target as HTMLInputElement).value))" />
    <button :disabled="disabled || modelValue >= max" @click="set(modelValue + 1)"><Icon name="plus" :size="12" :stroke="2.4" /></button>
  </div>
</template>

<style scoped>
.qty {
  display: inline-flex;
  border: 1px solid var(--c-border-2);
  border-radius: 6px;
  overflow: hidden;
  height: 32px;
  background: #fff;
}
.qty.small {
  height: 26px;
}
button {
  width: 30px;
  border: none;
  background: #fafafa;
  color: var(--c-text-2);
  display: flex;
  align-items: center;
  justify-content: center;
}
.small button {
  width: 24px;
}
button:hover:not(:disabled) {
  color: var(--c-primary);
}
button:disabled {
  color: #ccc;
  cursor: not-allowed;
}
input {
  width: 44px;
  border: none;
  border-left: 1px solid var(--c-border);
  border-right: 1px solid var(--c-border);
  text-align: center;
  outline: none;
  font-family: var(--font-num);
}
.small input {
  width: 38px;
  font-size: 13px;
}
</style>
