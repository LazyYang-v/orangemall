/**
 * 公共助手：通过网关 API 直接登录并注入 token（快、稳）；storageState 复用可选。
 * 依赖：http://localhost:8090（nginx + 网关容器栈）。
 */
import { test as base, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
const _test = base
export { _test as test, expect }

export interface Creds {
  phone: string
  password: string
}

export const CREDS = {
  user: { phone: '13800000000', password: '123456' },
  admin: { phone: '18800000000', password: 'admin123' },
}

export interface TokenPair {
  token: string
  profile: { id: string; nickname?: string; phone?: string }
}

/** 走真实网关登录（bypass UI） */
export async function apiLogin(c: Creds): Promise<TokenPair> {
  const res = await fetch('http://localhost:18080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(c),
  })
  const body = (await res.json()) as { code: number; data?: { token: string; user?: { id: string } } }
  if (body.code !== 0 || !body.data) throw new Error(`登录失败 code=${body.code}`)
  return { token: body.data.token, profile: (body.data as { user?: { id: string } }) as never }
}

/** 页面初始化前注入 om_token（orange-mall 前端读取的 localStorage key，JSON 序列化字符串） */
export async function injectToken(page: Page, t: string) {
  await page.addInitScript((tok) => {
    window.localStorage.setItem('om_token', JSON.stringify(tok))
    window.localStorage.setItem('om_token_v', JSON.stringify(tok)) // 兼容旧 key
  }, t)
}

export async function loginAs(page: Page, c: Creds) {
  const { token } = await apiLogin(c)
  await injectToken(page, token)
  return token
}
