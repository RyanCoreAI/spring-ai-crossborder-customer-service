import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  outputDir: 'test-results',
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.OMNI_FRONTEND_URL || 'http://127.0.0.1:5188',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: process.env.OMNI_E2E_EXTERNAL_SERVER
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 5188 --strictPort',
        url: 'http://127.0.0.1:5188/login',
        reuseExistingServer: true,
        timeout: 120_000,
      },
})
