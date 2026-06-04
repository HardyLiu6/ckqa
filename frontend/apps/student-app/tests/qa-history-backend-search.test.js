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

test('问答历史搜索下沉到后端 keyword 查询而不是本地过滤当前页', () => {
  assert.match(qaHistorySource, /searchKeyword:\s*searchKeyword\.value/)
  assert.match(qaHistorySource, /watch\(\s*searchKeyword,/)
  assert.doesNotMatch(qaHistorySource, /s\.title\.toLowerCase\(\)\.includes\(kw\)/)
  assert.doesNotMatch(qaHistorySource, /s\.lastMessage\.toLowerCase\(\)\.includes\(kw\)/)
})
