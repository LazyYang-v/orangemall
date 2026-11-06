/**
 * 秒杀可靠性面板冒烟（Phase 3 OM-013 / OM-019）：
 * 后台秒杀页可见消费者状态 / Kafka 积压 / 死信；暂停 → 已暂停 → 恢复 → 运行中（演练开关真实生效）。
 * 用例结束无论成败都经 API 恢复消费者，避免把栈留在暂停状态。
 */
import { CREDS, apiLogin, expect, injectToken, test } from '../helpers'

const GW = 'http://localhost:18080/api'

test.describe.configure({ mode: 'serial' })

test.describe('后台秒杀可靠性', () => {
  let token = ''

  test.beforeAll(async () => {
    token = (await apiLogin(CREDS.admin)).token
  })

  test.afterAll(async () => {
    await fetch(`${GW}/admin/seckill/consumer/resume`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
  })

  test('面板渲染：消费者运行中、积压与死信可见', async ({ page }) => {
    await injectToken(page, token)
    await page.goto('/admin/seckill')
    const card = page.locator('.adm-card.rel')
    await expect(card.locator('h3')).toContainText('可靠性')
    await expect(card.locator('.strip .cell').first()).toContainText('运行中')
    await expect(card.locator('.strip')).toContainText('Kafka 积压')
    await expect(card.locator('.strip')).toContainText(/\d+ 条/)
    await card.getByRole('button', { name: '全部' }).click()
    // 死信表：有记录则每行带「待处理/已处理」状态，无记录显示占位
    const rows = card.locator('.scroll tbody tr')
    await expect(rows.first()).toContainText(/待处理|已处理|暂无死信/)
  })

  test('演练开关：暂停 → 已暂停 → 恢复 → 运行中', async ({ page }) => {
    await injectToken(page, token)
    await page.goto('/admin/seckill')
    const cell = page.locator('.adm-card.rel .strip .cell').first()
    await expect(cell).toContainText('运行中')
    await cell.getByRole('button', { name: '暂停（演练）' }).click()
    await expect(cell).toContainText('已暂停')
    await cell.getByRole('button', { name: '恢复' }).click()
    await expect(cell).toContainText('运行中')
  })
})
