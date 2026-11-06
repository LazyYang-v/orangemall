import { test, expect, loginAs, CREDS } from '../helpers'

/** 01 · 游客浏览：首页/搜索/详情可达，关键区块渲染 */
test.describe('商城浏览冒烟', () => {
  test('首页加载并展示商品', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('.hero-row').first()).toBeVisible({ timeout: 15000 })
    await expect(page.locator('.promo-row .grid > *, .hero-row a, .grid > a').first()).toBeVisible({ timeout: 15000 })
  })

  test('搜索页可用', async ({ page }) => {
    await page.goto('/search?kw=手机')
    await expect(page.locator('.spu-card, [class*=card]').first()).toBeVisible({ timeout: 15000 })
  })

  test('商品详情可达（直达商品 ID）', async ({ page }) => {
    await page.goto('/product/1001')
    await expect(page.getByText(/果仁|Phone/i).first()).toBeVisible({ timeout: 15000 })
  })

  test('品牌馆/排行榜/领券中心可达', async ({ page }) => {
    const checks: Array<[string, string]> = [
      ['/brands', '.card.brand'],
      ['/rank', '.card.list .row'],
      ['/coupons', '.note'],
    ]
    for (const [path, sel] of checks) {
      await page.goto(path)
      await expect(page.locator(sel).first()).toBeVisible({ timeout: 15000 })
    }
  })
})

/** 02 · 登录态浏览：个人中心/订单列表 */
test.describe('登录态冒烟', () => {
  test('用户登录后打开订单页不含未授权跳转', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto('/orders')
    await expect(page).toHaveURL(/\/orders/)
    await expect(page.locator('.order.card, .tabs, .o-head').first()).toBeVisible({ timeout: 15000 })
  })

  test('个人中心展示资料与售后入口', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto('/user')
    await expect(page.getByText('我的售后')).toBeVisible({ timeout: 15000 })
  })

  test('我的售后页可打开（空态或列表）', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto('/user/refunds')
    await expect(page.locator('text=我的售后').first()).toBeVisible({ timeout: 15000 })
  })
})
