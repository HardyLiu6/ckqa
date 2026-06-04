import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const qaSideNavSource = readFileSync(
  resolve(__dirname, '../src/components/module-nav/QASideNav.vue'),
  'utf8',
)

test('问答副导航新建对话保留 courseId/mode 并移除 sessionId', () => {
  assert.match(
    qaSideNavSource,
    /import\s+\{\s*buildQaRouteQuery,\s*withoutQaSessionQuery\s*\}\s+from\s+['"]@\/views\/qa\/qa-route-query-model['"]/,
  )
  assert.match(
    qaSideNavSource,
    /router\.push\(\{\s*path:\s*['"]\/qa\/ask['"],\s*query:\s*withoutQaSessionQuery\(route\.query\)\s*\}\)/,
  )
  assert.doesNotMatch(
    qaSideNavSource,
    /function\s+createNew\(\)\s*\{\s*router\.push\(['"]\/qa\/ask['"]\)\s*\}/,
  )
})

test('问答副导航选择历史会话仍以历史 sessionId 跳转', () => {
  assert.match(
    qaSideNavSource,
    /import\s+\{\s*buildQaRouteQuery,\s*withoutQaSessionQuery\s*\}\s+from\s+['"]@\/views\/qa\/qa-route-query-model['"]/,
  )
  assert.match(
    qaSideNavSource,
    /const\s+queryPatch\s*=\s*\{\s*sessionId:\s*session\.id\s*\}/,
  )
  assert.match(
    qaSideNavSource,
    /router\.push\(\{\s*path:\s*['"]\/qa\/ask['"],\s*query:\s*buildQaRouteQuery\(route\.query,\s*queryPatch\)\s*\}\)/,
  )
})

test('问答副导航订阅会话变更事件并展示会话骨架屏', () => {
  assert.match(
    qaSideNavSource,
    /import\s+\{\s*onQaSessionsChanged\s*\}\s+from\s+['"]@\/views\/qa\/qa-session-events['"]/,
  )
  assert.match(
    qaSideNavSource,
    /onQaSessionsChanged\(\(\)\s*=>\s*\{\s*if\s*\(route\.path\.startsWith\(['"]\/qa['"]\)\)\s*\{\s*loadRecentSessions\(\)\s*\}/,
  )
  assert.match(
    qaSideNavSource,
    /onUnmounted\(\(\)\s*=>\s*\{\s*stopSessionChangeListener\?\.\(\)/,
  )
  assert.match(qaSideNavSource, /class="session-skeleton-list"/)
  assert.match(qaSideNavSource, /class="recent-pop-skeleton-list"/)
})

test('问答副导航搜索会重新请求后端会话库而不是只过滤当前页', () => {
  assert.match(
    qaSideNavSource,
    /import\s+\{[^}]*buildQaSideNavQueryParams[^}]*\}\s+from\s+['"]@\/components\/module-nav\/qa-side-nav-model['"]/,
  )
  assert.match(qaSideNavSource, /listQaSessions\(buildQaSideNavQueryParams\(\{\s*keyword:/)
  assert.match(qaSideNavSource, /watch\(\s*searchKeyword,/)
  assert.doesNotMatch(qaSideNavSource, /sessions\.value\.filter\(\(s\)\s*=>\s*s\.title\.toLowerCase\(\)\.includes/)
})
