import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from '@playwright/test'

function parseArgs(argv) {
  const values = new Map()
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]
    const value = argv[index + 1]
    if (!key?.startsWith('--') || !value) throw new Error(`无效参数：${key ?? ''}`)
    values.set(key.slice(2), value)
  }
  const required = ['student-url', 'admin-url', 'course-id', 'material-id', 'kb-id', 'build-run-id', 'out']
  for (const key of required) {
    if (!values.get(key)) throw new Error(`缺少参数 --${key}`)
  }
  return Object.fromEntries(values)
}

async function settle(page, selector) {
  await page.locator(selector).first().waitFor({ state: 'visible', timeout: 30_000 })
  await page.waitForTimeout(1200)
}

async function applyPrivacyMask(page) {
  await page.addStyleTag({ content: `
    .avatar, .identity-avatar, .topbar-identity, .user-dropdown,
    .msg-meta, .index-run-mini-card__id, .mono { visibility: hidden !important; }
  ` })
  await page.evaluate(() => {
    const privatePattern = /(student\.|admin\.|teacher\.|STU\d+|ADM\d+|TCH\d+|crs-\d{8}-[\w-]+|session[_ -]?\d+|build[_ -]?\d+)/i
    for (const element of document.querySelectorAll('span,small,p,code')) {
      if (privatePattern.test(element.textContent || '')) element.style.visibility = 'hidden'
    }
  })
}

async function loginStudent(page, baseUrl) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '体验账号一键填入' }).click()
  await page.getByRole('button', { name: /登录学习空间/ }).click()
  await page.waitForURL(/\/home/, { timeout: 20_000 })
}

async function loginAdmin(page, baseUrl) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: /登录/ }).last().click()
  await page.waitForURL(/\/app\//, { timeout: 20_000 })
}

async function ensureStudentAnswer(page, options) {
  const url = `${options['student-url']}/qa/ask?courseId=${encodeURIComponent(options['course-id'])}&mode=basic`
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await settle(page, '.composer-input')
  await page.locator('.composer-input').fill('请给出死锁的定义，并说明产生死锁必须满足的四个条件。')
  await page.locator('.composer-input').press('Enter')
  await page.locator('.ai-bubble').filter({ hasText: '死锁' }).last().waitFor({ state: 'visible', timeout: 180_000 })
  await page.locator('.source-cards summary').last().click().catch(() => {})
  await applyPrivacyMask(page)
  await page.locator('.qa-ask-page').screenshot({
    path: path.join(options.out, 'student-qa-demo.png'),
  })
}

async function captureAdmin(page, options) {
  await page.goto(`${options['admin-url']}/app/materials/${encodeURIComponent(options['material-id'])}`, { waitUntil: 'domcontentloaded' })
  await settle(page, '.material-detail-grid')
  await applyPrivacyMask(page)
  await page.locator('.module-page').screenshot({ path: path.join(options.out, 'admin-materials.png') })

  const buildUrl = `${options['admin-url']}/app/knowledge-bases/${encodeURIComponent(options['kb-id'])}/build?buildRunId=${encodeURIComponent(options['build-run-id'])}&step=index`
  await page.goto(buildUrl, { waitUntil: 'domcontentloaded' })
  await settle(page, '.build-step-index__done')
  await applyPrivacyMask(page)
  await page.locator('.module-page').screenshot({ path: path.join(options.out, 'admin-build-smoke-demo.png') })
}

const options = parseArgs(process.argv.slice(2))
options.out = path.resolve(options.out)
await mkdir(options.out, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  const student = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
  await loginStudent(student, options['student-url'])
  await ensureStudentAnswer(student, options)

  const admin = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 })
  await loginAdmin(admin, options['admin-url'])
  await captureAdmin(admin, options)
} finally {
  await browser.close()
}
