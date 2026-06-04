import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const homeSource = readFileSync(resolve(__dirname, '../src/views/index.vue'), 'utf8')
const productLayoutSource = readFileSync(resolve(__dirname, '../src/layouts/ProductLayout.vue'), 'utf8')

test('首页接入真实用户、课程、问答会话与统计数据', () => {
  assert.match(homeSource, /import\s+\{\s*useCourseStore,\s*useUserStore\s*\}\s+from\s+['"]@\/stores['"]/)
  assert.match(homeSource, /import\s+\{\s*listQaSessions,\s*getQaSessionStats\s*\}\s+from\s+['"]@\/api\/qa['"]/)
  assert.match(homeSource, /onMounted\(\(\)\s*=>\s*\{\s*loadHomeData\(\)/)
  assert.match(homeSource, /courseStore\.loadCourses\(\{\s*force:\s*true\s*\}\)/)
  assert.match(homeSource, /buildHomeRecentQaItems\(recentSessionPayload\.value,\s*courseNameById\.value/)
  assert.doesNotMatch(homeSource, /const\s+greetingName\s*=\s*['"]俊达['"]/)
  assert.doesNotMatch(homeSource, /const\s+recentQAs\s*=\s*\[/)
})

test('首页最近问答跳转到真实问答会话入口', () => {
  assert.match(homeSource, /router\.push\(q\.route\)/)
  assert.doesNotMatch(homeSource, /\/qa\/detail/)
})

test('首页和 ProductLayout 不再因为 100vh 叠加顶栏撑出滚动条', () => {
  assert.match(productLayoutSource, /\.product-main\s*\{[\s\S]*?box-sizing:\s*border-box/)
  assert.match(homeSource, /\.home-page\s*\{[\s\S]*?min-height:\s*calc\(100vh - 64px\)/)
  assert.match(homeSource, /\.home-page\s*\{[\s\S]*?overflow-x:\s*hidden/)
  assert.doesNotMatch(homeSource, /\.home-page\s*\{[\s\S]*?min-height:\s*100vh/)
})
