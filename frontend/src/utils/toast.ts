import { reactive } from 'vue'

export type ToastType = 'info' | 'success' | 'error' | 'warning'
export interface ToastItem {
  id: number
  type: ToastType
  text: string
}

export const toastState = reactive<{ list: ToastItem[] }>({ list: [] })
let seed = 0

function push(type: ToastType, text: string, duration = 2400) {
  const id = ++seed
  toastState.list.push({ id, type, text })
  setTimeout(() => {
    const i = toastState.list.findIndex((t) => t.id === id)
    if (i >= 0) toastState.list.splice(i, 1)
  }, duration)
}

export const toast = {
  info: (t: string) => push('info', t),
  success: (t: string) => push('success', t),
  error: (t: string) => push('error', t, 3200),
  warning: (t: string) => push('warning', t, 3000),
}
