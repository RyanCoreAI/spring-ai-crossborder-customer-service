import { expect, test } from '@playwright/test'

const email = process.env.OMNI_ADMIN_EMAIL
const password = process.env.OMNI_ADMIN_PASSWORD

test.describe('authenticated demo flows', () => {
  test.skip(!email || !password, 'Requires the real compose.demo.yml backend and bootstrap credentials.')

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('邮箱').fill(email!)
    await page.getByLabel('密码').fill(password!)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/admin(?:$|\?)/)
    await expect(page.locator('.topbar h1')).toHaveText('数据概览')
  })

  const routes = [
    ['/admin/inbox', '统一收件箱'],
    ['/admin/actions', '动作审批'],
    ['/admin/rag-workbench', 'RAG 工作台'],
    ['/admin/traces', '轨迹回放'],
    ['/admin/multilingual', '多语言证据'],
    ['/admin/channels', '渠道状态'],
    ['/admin/sre', '生产健康'],
  ] as const

  for (const [path, title] of routes) {
    test(`loads ${title} from real backend DTOs`, async ({ page }) => {
      await page.goto(path)
      await expect(page.locator('.topbar h1')).toHaveText(title)
      await expect(page.getByText('商家与平台人员登录')).toHaveCount(0)
      await expect(page.locator('.ant-result-403')).toHaveCount(0)
      await expect(page.locator('.ant-message-error')).toHaveCount(0)
    })
  }

  test('renders dashboard distributions from real operation metrics', async ({ page }) => {
    await expect(page.getByText('咨询意图分布')).toBeVisible()
    await expect(page.getByText('接入渠道分布')).toBeVisible()
    const charts = page.locator('.analytics-card canvas')
    await expect(charts).toHaveCount(2)
    const sizes = await charts.evaluateAll((elements) =>
      elements.map((element) => ({
        width: (element as HTMLCanvasElement).width,
        height: (element as HTMLCanvasElement).height,
      })),
    )
    expect(sizes.every((size) => size.width > 0 && size.height > 0)).toBeTruthy()
  })
})
