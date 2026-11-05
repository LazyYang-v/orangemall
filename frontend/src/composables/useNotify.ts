import { onUnmounted, watch } from 'vue'
import type { NotifyMessage } from '@/types/notify'
import { USE_MOCK } from '@/api/http'
import { useUserStore } from '@/stores/user'

/**
 * 订阅 WebSocket 推送：物流轨迹更新、订单状态变化、秒杀结果。
 * 真实环境连接 /ws/notify?token=xxx，断线 3s 重连；Mock 环境订阅内存事件总线。
 */
export function useNotify(handler: (msg: NotifyMessage) => void) {
  const user = useUserStore()
  let disposed = false
  let ws: WebSocket | undefined
  let unsubscribe: (() => void) | undefined
  let retry: ReturnType<typeof setTimeout> | undefined

  async function connect() {
    unsubscribe?.()
    if (ws) {
      ws.onclose = null
      ws.close()
    }
    if (disposed || !user.profile) return
    if (USE_MOCK) {
      const { mockBus } = await import('@/mock/bus')
      unsubscribe = mockBus.subscribe(user.profile.id, handler)
      return
    }
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    ws = new WebSocket(`${proto}://${location.host}/ws/notify?token=${encodeURIComponent(user.token ?? '')}`)
    ws.onmessage = (e) => {
      try {
        handler(JSON.parse(e.data) as NotifyMessage)
      } catch {
        /* ignore malformed */
      }
    }
    ws.onclose = () => {
      if (!disposed) retry = setTimeout(connect, 3000)
    }
  }

  watch(() => user.profile?.id, connect, { immediate: true })
  onUnmounted(() => {
    disposed = true
    clearTimeout(retry)
    unsubscribe?.()
    ws?.close()
  })
}
