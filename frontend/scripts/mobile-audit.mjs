/** 375px 横向溢出诊断：列出 document scrollWidth 与最宽元素 */
import { chromium } from 'playwright-core'

const PAGES = ['/', '/search?kw=手机', '/product/1001', '/cart', '/coupons', '/seckill', '/points', '/rank', '/brands', '/orders', '/checkout?items=100101:1', '/user', '/user/refunds', '/user/coupons', '/user/favorites', '/user/addresses', '/orders/2120394857100002561', '/seckill/1', '/search?kw=%E8%80%B3%E6%9C%BA']

// Phase 4：运营后台同样要求 375px 无横滑（宽表格在卡片内滚动）
const ADMIN_PAGES = ['/admin', '/admin/orders', '/admin/refunds', '/admin/users', '/admin/risk', '/admin/products', '/admin/categories', '/admin/coupons', '/admin/seckill', '/admin/cache', '/admin/logs']

let worst = 0
async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  await audit(browser, '13800000000', '123456', PAGES)
  await audit(browser, '18800000000', 'admin123', ADMIN_PAGES)
  await browser.close()
  if (process.env.STRICT && worst > 0) { console.error(`横向溢出最大 ${worst}px`); process.exit(1) }
}

async function audit(browser, phone, password, pages) {
  const ctx = await browser.newContext({ viewport: { width: 375, height: 812 } })
  const page = await ctx.newPage()
  // 登录
  const login = await fetch('http://localhost:18080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone, password }) })
  const { data } = await login.json()
  await page.addInitScript((tok) => window.localStorage.setItem('om_token', JSON.stringify(tok)), data.token)
  for (const p of pages) {
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
    worst = Math.max(worst, r.over)
    console.log(`${p}  overflow=${r.over}px  offenders: ${r.bad.join('  ')}`)
  }
  await ctx.close()
}
main().catch((e) => { console.error(e); process.exit(1) })
