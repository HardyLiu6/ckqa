import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const qaPageSource = readFileSync(
  resolve(__dirname, '../src/views/qa/index.vue'),
  'utf8',
)

function extractFunctionBody(source, functionName) {
  const start = source.indexOf(`function ${functionName}`)
  assert.notEqual(start, -1, `missing function ${functionName}`)
  const bodyStart = source.indexOf('{', start)
  let depth = 0
  for (let index = bodyStart; index < source.length; index += 1) {
    const char = source[index]
    if (char === '{') depth += 1
    if (char === '}') depth -= 1
    if (depth === 0) {
      return source.slice(bodyStart + 1, index)
    }
  }
  throw new Error(`unterminated function ${functionName}`)
}

test('课程切换只更新 URL，由 route watch 负责加载知识库，避免重复请求', () => {
  const body = extractFunctionBody(qaPageSource, 'handleCourseChange')

  assert.match(body, /router\.replace\(/)
  assert.doesNotMatch(body, /loadKnowledgeBases\(courseId/)
})

test('移除 sessionId 且课程不变时不重新选择默认知识库', () => {
  assert.match(
    qaPageSource,
    /if\s*\(previousSessionId\s*&&\s*!sessionId\)\s*\{[\s\S]*?if\s*\(courseId\s*!==\s*selectedCourseId\.value\)\s*\{[\s\S]*?await\s+syncCourseScopeFromQuery\(courseId\)[\s\S]*?\}/,
  )
})

test('知识库加载与历史会话恢复忽略过期响应', () => {
  assert.match(qaPageSource, /let\s+knowledgeBaseLoadRequestId\s*=\s*0/)
  assert.match(qaPageSource, /let\s+sessionRestoreRequestId\s*=\s*0/)
  assert.match(qaPageSource, /requestId\s*!==\s*knowledgeBaseLoadRequestId/)
  assert.match(qaPageSource, /function\s+isCurrentSessionRestore\(/)
  assert.match(qaPageSource, /isCurrentSessionRestore\(requestId,\s*sessionId\)/)
})

test('恢复历史会话后补齐 URL courseId，让侧栏新建对话继承课程上下文', () => {
  assert.match(qaPageSource, /function\s+syncRestoredSessionRouteQuery\(/)
  assert.match(
    qaPageSource,
    /query:\s*buildQaRouteQuery\(route\.query,\s*\{[\s\S]*?courseId:\s*session\.courseId,[\s\S]*?sessionId,[\s\S]*?\}\)/,
  )
  assert.match(
    qaPageSource,
    /await\s+syncRestoredSessionRouteQuery\(session,\s*sessionId\)/,
  )
})
