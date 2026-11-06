/** 375px 横向溢出诊断：列出 document scrollWidth 与最宽元素 */
import { chromium } from 'playwright-core'

const PAGES = ['/', '/search?kw=手机', '/product/1001', '/cart', '/coupons', '/seckill', '/points', '/rank', '/brands', '/orders', '/checkout?items=100101:1', '/user', '/user/refunds', '/user/coupons', '/user/favorites', '/user/addresses', '/orders/2120394857100002561', '/seckill/1', '/search?kw=%E8%80%B3%E6%9C%BA']

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const ctx = await browser.newContext({ viewport: { width: 375, height: 812 } })
  const page = await ctx.newPage()
  // 登录
  const login = await fetch('http://localhost:18080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone: '13800000000', password: '123456' }) })
  const { data } = await login.json()
  await page.addInitScript((tok) => window.localStorage.setItem('om_token', JSON.stringify(tok)), data.token)
  for (const p of PAGES) {
    await page.goto('http://localhost:8090' + p)
    await page.waitForTimeout(1200)
    const r = await page.evaluate(() => {
      const over = document.documentElement.scrollWidth - document.documentElement.clientWidth
      const bad = []
      for (const el of document.querySelectorAll('body *')) {
        const w = el.scrollWidth
        if (w > document.documentElement.clientWidth + 1 && w < 2000) {
          const cls = (el.className && typeof el.className === 'string') ? el.className.split(' ')[0] : el.tagName
          bad.push(`${el.tagName.toLowerCase()}.${cls}(w=${w})`)
        }
      }
      return { over, bad: [...new Set(bad)].slice(0, 6) }
    })
    console.log(`${p}  overflow=${r.over}px  offenders: ${r.bad.join('  ')}`)
  }
  await browser.close()
}
main().catch((e) => { console.error(e); process.exit(1) })
