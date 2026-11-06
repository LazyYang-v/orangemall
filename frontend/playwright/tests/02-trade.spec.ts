import { test, expect, loginAs, CREDS } from '../helpers'

/** 交易链路冒烟：加购 → 结算 → 沙箱支付 → 申请售后 → admin 审批；用例间共享订单号，必须串行 */
const SKU = '100101' // 果仁 Phone 默认规格（种子数据，库存充足）

test.describe.configure({ mode: 'serial' })

test.describe('交易链路冒烟', () => {
  let orderId = ''

  test('详情加购 → 购物车可见', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto('/product/1001')
    await page.locator('button.btn-cart').click({ timeout: 15000 })
    await expect(page.getByText('已加入购物车').first()).toBeVisible({ timeout: 10000 })
    await page.goto('/cart')
    await expect(page.locator('.shop.card .row').first()).toBeVisible({ timeout: 15000 })
  })

  test('结算页渲染地址与应付金额', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto(`/checkout?items=${SKU}:1`)
    await expect(page.locator('button:has-text("提交订单")')).toBeVisible({ timeout: 15000 })
    await expect(page.locator('.price').first()).toBeVisible()
  })

  test('下单 → 收银台 → 沙箱支付成功', async ({ page }) => {
    await loginAs(page, CREDS.user)
    await page.goto(`/checkout?items=${SKU}:1`)
    await page.locator('button:has-text("提交订单")').click({ timeout: 15000 })
    await page.waitForURL(/\/pay\/\d+/, { timeout: 20000 })
    orderId = new URL(page.url()).pathname.split('/').pop() ?? ''
    expect(orderId, 'pay URL 应携带订单号').toMatch(/^\d+$/)
    await page.locator('button:has-text("确认支付")').click({ timeout: 15000 })
    await page.locator('button:has-text("模拟扫码支付成功")').click({ timeout: 15000 })
    await page.waitForURL(/\/pay\/result\//, { timeout: 20000 })
    await expect(page.getByText('支付成功').first()).toBeVisible({ timeout: 15000 })
  })

  test('发起售后 → 我的售后出现待审批', async ({ page }) => {
    expect(orderId).toBeTruthy()
    await loginAs(page, CREDS.user)
    await page.goto(`/orders/${orderId}`)
    await page.locator('button:has-text("申请售后")').first().click({ timeout: 15000 })
    await page.locator('button:has-text("仅退款")').click()
    await page.locator('textarea').fill('Playwright 冒烟：商品未拆封不想要了')
    await page.locator('button:has-text("提交申请")').click()
    await expect(page.getByText(/售后处理中|退款申请中|待审批/).first()).toBeVisible({ timeout: 15000 })
    await page.goto('/user/refunds')
    await expect(page.getByText('待审批').first()).toBeVisible({ timeout: 15000 })
  })

  test('admin 审批通过 → 退款中/已退款', async ({ page }) => {
    expect(orderId).toBeTruthy()
    await loginAs(page, CREDS.admin)
    await page.goto(`/admin/refunds`)
    await expect(page.locator('h2:has-text("售后管理")')).toBeVisible({ timeout: 15000 })
    const row = page.locator('tr', { hasText: orderId })
    await row.locator('button:has-text("审批")').click({ timeout: 15000 })
    await page.locator('input[placeholder*="核实"]').fill('Playwright 冒烟审批通过')
    await page.locator('button:has-text("通过退款")').click()
    await expect(row.getByText(/退款中|已退款/)).toBeVisible({ timeout: 15000 })
    // 沙箱渠道 2s 后回调 → 订单变为已退款
    await expect.poll(async () => {
      await page.reload()
      return (await page.locator('tr', { hasText: orderId }).innerText()).includes('已退款')
    }, { timeout: 20000, intervals: [2000] }).toBe(true)
  })

  test('admin 用户管理：列表 + 详情/封禁按钮', async ({ page }) => {
    await loginAs(page, CREDS.admin)
    await page.goto('/admin/users')
    await expect(page.locator('h2:has-text("用户管理")')).toBeVisible({ timeout: 15000 })
    await expect(page.locator('button:has-text("详情")').first()).toBeVisible({ timeout: 15000 })
    await expect(page.locator('button:has-text("封禁"), button:has-text("解封")').first()).toBeVisible()
  })
})
