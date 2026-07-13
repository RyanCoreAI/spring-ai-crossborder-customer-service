import { expect, test } from '@playwright/test'

test('login surface is compact and usable', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '商家与平台人员登录' })).toBeVisible()
  await expect(page.locator('button[type="submit"]')).toBeVisible()
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
})

test('buyer widget exposes a real session form without fake connection state', async ({ page }) => {
  await page.goto('/widget')
  await expect(page.getByText('未连接')).toBeVisible()
  await expect(page.getByRole('button', { name: '开始咨询' })).toBeVisible()
  await expect(page.getByText('已连接')).toHaveCount(0)
})
