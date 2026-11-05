import axios, { type AxiosRequestConfig } from 'axios'
import type { Result } from '@/types/domain'
import { KEYS, storage } from '@/utils/storage'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message)
  }
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
  ...(USE_MOCK ? { adapter: async (cfg) => (await import('@/mock/adapter')).mockAdapter(cfg) } : {}),
})

instance.interceptors.request.use((config) => {
  const token = storage.get<string | null>(KEYS.token, null)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized: (() => void) | undefined
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

instance.interceptors.response.use(
  (res) => {
    const body = res.data as Result<unknown>
    if (body.code === 0) return res
    if (body.code === 401) onUnauthorized?.()
    return Promise.reject(new ApiError(body.code, body.message))
  },
  (err) => {
    if (err.response?.status === 401) onUnauthorized?.()
    if (err.response?.status === 429) return Promise.reject(new ApiError(4290, '当前访问人数过多，请稍后再试'))
    return Promise.reject(new ApiError(err.response?.status ?? -1, err.message === 'Network Error' ? '网络异常，请检查网络连接' : err.message))
  },
)

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await instance.request<Result<T>>(config)
  return res.data.data
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ url, method: 'get', params }),
  post: <T>(url: string, data?: object) => request<T>({ url, method: 'post', data }),
  put: <T>(url: string, data?: object) => request<T>({ url, method: 'put', data }),
  delete: <T>(url: string, data?: object) => request<T>({ url, method: 'delete', data }),
}
