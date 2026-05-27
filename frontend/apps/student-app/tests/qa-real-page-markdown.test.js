import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const qaPageSource = readFileSync(resolve(__dirname, '../src/views/qa/index.vue'), 'utf8')

test('真实问答页只对 assistant 消息使用受控 Markdown 组件', () => {
  assert.match(qaPageSource, /import QaMarkdownContent from '\.\/QaMarkdownContent\.vue'/)
  assert.match(qaPageSource, /<QaMarkdownContent :content="msg\.content" \/>/)
  assert.match(qaPageSource, /class="source-cards"/)
  assert.match(qaPageSource, /class="source-type"/)
  assert.match(qaPageSource, /sourceTypeLabel\(source\)/)
  assert.match(qaPageSource, /graphrag_report/)
  assert.match(qaPageSource, /global_fallback_text_unit/)
  assert.match(qaPageSource, /source\.snippet/)
  assert.match(qaPageSource, /<div class="msg-text">\{\{ msg\.content \}\}<\/div>/)
})

test('真实问答页恢复历史会话时会重新接入运行中任务', () => {
  assert.match(qaPageSource, /resumeRunningTaskFromMessages/)
  assert.match(qaPageSource, /getQaTask\(sessionId, runningUserMessage\.taskId\)/)
  assert.match(qaPageSource, /startTaskStream\(sessionId, runningUserMessage\.taskId, pendingTask\.value\)/)
  assert.match(qaPageSource, /Math\.min\(2, resolvePollingDelaySeconds/)
})

test('真实问答页会展示可折叠的检索过程日志', () => {
  assert.match(qaPageSource, /<details v-if="msg\.latestLogs\?\.length" class="process-cards">/)
  assert.match(qaPageSource, /<details v-if="pendingProcessLogs\.length" class="process-cards process-cards-pending" open>/)
  assert.match(qaPageSource, /formatTaskLogLine\(logLine\)/)
  assert.match(qaPageSource, /streamed chunk count=/)
  assert.match(qaPageSource, /finished graphrag query --method/)
})

test('真实问答页在回答成功后刷新长期学习记忆列表', () => {
  assert.match(qaPageSource, /await loadMemoryState\(selectedCourseId\.value, selectedKnowledgeBaseId\.value\)/)
  assert.match(qaPageSource, /学习记忆/)
  assert.match(qaPageSource, /Local 问答会按问题动态使用学习记忆/)
  assert.match(qaPageSource, /memoryEnabled/)
})
