import { test, expect } from '@playwright/test'

import { loginAsAdmin } from './fixtures/auth.js'
import { PAGES } from './fixtures/m8-mocks.js'

// M8 Task 6：DOM 文本术语巡检兜底。
//
// 配合 src/copy/admin.test.js 的静态属性测试——后者只覆盖常量树中的字段，
// 任何直写在 .vue 模板里的中文文本都绕过那层断言；本 spec 在真实渲染后的
// DOM 文本上再扫一遍设计稿 §10.2 的禁词清单，闭合"文案集中地未被引用"的盲区。

const FORBIDDEN_TERMS = [
  '冒烟',
  'embedding',
  '实体抽取',
  'P95',
  'MinerU',
  'smoke',
]

// DOM 中允许出现禁词的合法上下文（如系统健康页保留的"专业表达"段）。
// 每条 allow 描述一处允许命中：pageKey + match + reason；为防止误放行，
// 同时记录 sample 关键片段，命中点必须包含该片段才放行。
const ALLOWED_HITS = []

function isAllowed(pageKey, term, sample) {
  return ALLOWED_HITS.some((rule) =>
    rule.pageKey === pageKey
    && rule.match === term
    && sample.includes(rule.sample ?? rule.match),
  )
}

for (const pageDef of PAGES) {
  test(`M8 文案巡检：${pageDef.key} DOM 文本不含工程术语`, async ({ page }) => {
    await loginAsAdmin(page, { mocks: pageDef.mocks })
    await page.goto(pageDef.path)
    await expect(page.locator(pageDef.ready)).toBeVisible()
    await page.waitForLoadState('networkidle')

    const bodyText = await page.locator('body').innerText()
    const hits = []
    for (const term of FORBIDDEN_TERMS) {
      const re = new RegExp(term, 'i')
      const match = bodyText.match(re)
      if (!match) continue
      const idx = match.index ?? 0
      const sample = bodyText.slice(Math.max(0, idx - 20), idx + term.length + 20)
      if (isAllowed(pageDef.key, term, sample)) continue
      hits.push({ term, sample })
    }
    expect(hits, JSON.stringify(hits, null, 2)).toEqual([])
  })
}
