/**
 * 模拟 WebSocket 推送通道（真实环境：/ws/notify，由通知服务消费 MQ 物流/订单事件后推送）。
 */
import type { NotifyMessage } from '@/types/notify'

type Listener = (msg: NotifyMessage) => void
const listeners = new Map<string, Set<Listener>>()

export const mockBus = {
  subscribe(userId: string, fn: Listener) {
    if (!listeners.has(userId)) listeners.set(userId, new Set())
    listeners.get(userId)!.add(fn)
    return () => listeners.get(userId)?.delete(fn)
  },
  publish(userId: string, msg: NotifyMessage) {
    listeners.get(userId)?.forEach((fn) => fn(msg))
  },
}
