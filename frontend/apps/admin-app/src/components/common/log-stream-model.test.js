import test from 'node:test'
import assert from 'node:assert/strict'

import {
  LOG_LEVELS,
  normalizeLogLines,
  sanitizeLogMessage,
  shouldAutoFollow,
  resolveLevelTone,
} from './log-stream-model.js'

test('LOG_LEVELS 暴露 4 级', () => {
  assert.deepEqual(LOG_LEVELS, ['info', 'warn', 'error', 'debug'])
})

test('normalizeLogLines 截断至 cap，保留最近的行', () => {
  const lines = Array.from({ length: 600 }, (_, i) => ({ message: `line-${i}` }))
  const out = normalizeLogLines(lines, { cap: 100 })
  assert.equal(out.length, 100)
  assert.equal(out.at(-1).message, 'line-599')
  assert.equal(out.at(0).message, 'line-500')
})

test('normalizeLogLines 补齐默认字段与 id', () => {
  const [line] = normalizeLogLines([{ message: '你好' }])
  assert.equal(line.level, 'info')
  assert.match(line.id, /^log-/)
  assert.ok(Number.isFinite(line.timestamp))
  assert.equal(line.message, '你好')
})

test('normalizeLogLines 空 / 非数组返回空数组', () => {
  assert.deepEqual(normalizeLogLines(null), [])
  assert.deepEqual(normalizeLogLines(undefined), [])
  assert.deepEqual(normalizeLogLines('x'), [])
})

test('sanitizeLogMessage 清洗 embedding / 实体抽取 / MinerU / P95', () => {
  assert.equal(sanitizeLogMessage('Start embedding chunks...'), 'Start 构建检索索引 chunks...')
  assert.equal(sanitizeLogMessage('实体抽取进行中'), '识别课程概念进行中')
  assert.equal(sanitizeLogMessage('MinerU 调用超时'), 'PDF 解析 调用超时')
  assert.equal(sanitizeLogMessage('P95 latency 312ms'), '高负载响应 312ms')
  assert.equal(sanitizeLogMessage('smoke test started'), '知识库验证 started')
  assert.equal(sanitizeLogMessage('冒烟测试启动'), '知识库验证启动')
})

test('sanitizeLogMessage 容忍空与非字符串', () => {
  assert.equal(sanitizeLogMessage(null), '')
  assert.equal(sanitizeLogMessage(undefined), '')
  assert.equal(sanitizeLogMessage(42), '42')
})

test('shouldAutoFollow 近期出现 ERROR 行时返回 false（暂停自动滚动）', () => {
  const now = 10_000
  const lines = [{ level: 'error', timestamp: now - 1000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, 0, now, 8000), false)
})

test('shouldAutoFollow 过了暂停窗口后恢复自动滚动', () => {
  const now = 20_000
  const lines = [{ level: 'error', timestamp: 1_000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, 0, now, 8000), true)
})

test('shouldAutoFollow 用户手动往上滚后暂停自动滚动', () => {
  const now = 10_000
  const lines = [{ level: 'info', timestamp: 1_000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, now - 2000, now), false)
  assert.equal(shouldAutoFollow(lines, now - 15_000, now), true)
})

test('shouldAutoFollow 空 lines 默认跟随', () => {
  assert.equal(shouldAutoFollow([], 0, 1000), true)
  assert.equal(shouldAutoFollow(null, 0, 1000), true)
})

test('resolveLevelTone 映射级别到 tone', () => {
  assert.equal(resolveLevelTone('info'), 'neutral')
  assert.equal(resolveLevelTone('warn'), 'warning')
  assert.equal(resolveLevelTone('error'), 'danger')
  assert.equal(resolveLevelTone('debug'), 'blocked')
  assert.equal(resolveLevelTone('???'), 'neutral')
})
