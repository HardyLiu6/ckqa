import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const qaHistorySource = readFileSync(
  resolve(__dirname, '../src/views/qa/QAHistory.vue'),
  'utf8',
)

test('问答历史页会在会话操作成功后通知近期对话刷新', () => {
  assert.match(
    qaHistorySource,
    /import\s+\{\s*notifyQaSessionsChanged\s*\}\s+from\s+['"]\.\/qa-session-events['"]/,
  )
  assert.match(qaHistorySource, /notifyHistorySessionChanged\(['"]favorite['"],\s*session/)
  assert.match(qaHistorySource, /notifyHistorySessionChanged\(['"]rename['"],\s*updated/)
  assert.match(qaHistorySource, /notifyHistorySessionChanged\(['"]archive['"],\s*\{\s*\.\.\.session,\s*status:\s*nextStatus\s*\}/)
})
