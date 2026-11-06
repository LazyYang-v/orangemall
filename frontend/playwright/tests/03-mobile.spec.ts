import { test, expect, loginAs, CREDS } from '../helpers'

/** 04 · 375px 移动端断点：关键页无横向滚动（OM-023 DoD 375px 可用） */
test.describe('移动端 375 冒烟', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
  })

  const PAGES: Array<[string, RegExp]> = [
    ['/', /橙选|OrangeMall/i],
    ['/search?kw=手机', /.*/],
    ['/product/1001', /.*/],
    ['/coupons', /.*/],
    ['/seckill', /.*/],
    ['/brands', /.*/],
    ['/rank', /.*/],
  ]

  for (const [path] of PAGES) {
    test(`游客 375px 不横向滚动：${path}`, async ({ page }) => {
      await page.goto(path)
      await page.waitForTimeout(800)
      const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(over, `${path} 横向溢出 ${over}px`).toBeLessThanOrEqual(1)
    })
  }

  const AUTH_PAGES = ['/orders', '/cart', '/checkout?items=100101:1', '/user', '/user/refunds', '/user/coupons', '/user/addresses', '/points']
  for (const path of AUTH_PAGES) {
    test(`登录用户 375px 不横向滚动：${path}`, async ({ page }) => {
      await loginAs(page, CREDS.user)
      await page.goto(path)
      await page.waitForTimeout(800)
      const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(over, `${path} 横向溢出 ${over}px`).toBeLessThanOrEqual(1)
    })
  }
})

/** 05 · admin 后台 375（admin 使用桌面布局，仅要求可用不炸布局） */
test.describe('admin 375', () => {
  test('admin 侧栏在 375px 可用（不白屏且主区可见）', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await loginAs(page, CREDS.admin)
    await page.goto('/admin')
    await expect(page.locator('.adm-title, h2').first()).toBeVisible({ timeout: 15000 })
  })
})
