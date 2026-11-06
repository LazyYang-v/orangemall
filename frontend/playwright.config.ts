import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './playwright/tests',
  timeout: 45_000,
  retries: 1,
  workers: 1,
  use: {
    baseURL: 'http://localhost:8090',
    // 本地用系统 Chrome；CI 用 `npx playwright install chromium` 安装的内置 Chromium
    channel: process.env.CI ? undefined : 'chrome',
    headless: true,
    viewport: { width: 1280, height: 800 },
    screenshot: 'only-on-failure',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
})
