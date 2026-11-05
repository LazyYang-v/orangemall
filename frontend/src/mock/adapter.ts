import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { dispatch, MockError } from './server'

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/** axios 自定义 adapter：拦截所有请求交给 Mock 路由，返回统一 Result 结构 */
export async function mockAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  const url = new URL(config.url ?? '', 'http://mock.local')
  const base = (config.baseURL ?? '').replace(/\/$/, '')
  let path = url.pathname
  if (base && path.startsWith(base)) path = path.slice(base.length) || '/'
  const query: Record<string, string> = {}
  url.searchParams.forEach((v, k) => (query[k] = v))
  Object.entries((config.params ?? {}) as Record<string, unknown>).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') query[k] = String(v)
  })
  const body = typeof config.data === 'string' ? JSON.parse(config.data || '{}') : (config.data ?? {})
  const auth = (config.headers?.Authorization as string | undefined)?.replace(/^Bearer /, '')

  await sleep(80 + Math.random() * 220)

  let data: unknown
  try {
    const result = dispatch((config.method ?? 'get').toUpperCase(), path, query, body, auth)
    // 与真实 HTTP 一致：响应体是序列化后的副本，避免页面拿到 Mock DB 的对象引用（会导致误改数据、Vue 认为值未变化而不刷新）
    data = { code: 0, message: 'ok', data: result === undefined ? null : JSON.parse(JSON.stringify(result)), traceId: Math.random().toString(16).slice(2, 18) }
  } catch (e) {
    if (e instanceof MockError) data = { code: e.code, message: e.message, data: null }
    else {
      console.error('[mock]', e)
      data = { code: 500, message: '服务器开小差了', data: null }
    }
  }
  if (import.meta.env.DEV) console.debug('[mock]', config.method?.toUpperCase(), path, query, body, '→', data)
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}
