import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatRelativeTime,
  isResumableBuildRun,
  pickResumableBuildRuns,
  toResumeCard,
} from '../../views/pages/resume-build-model.js'

test('isResumableBuildRun 仅认 pending/running', () => {
  assert.equal(isResumableBuildRun({ status: 'pending', currentStage: 'material_selection' }), true)
  assert.equal(isResumableBuildRun({ status: 'running', currentStage: 'parse' }), true)
  assert.equal(isResumableBuildRun({ status: 'success', currentStage: 'done' }), false)
  assert.equal(isResumableBuildRun({ status: 'failed', currentStage: 'index' }), false)
})

test('isResumableBuildRun 阶段为 done 时不可继续', () => {
  assert.equal(isResumableBuildRun({ status: 'running', currentStage: 'done' }), false)
})

test('isResumableBuildRun 状态大小写不敏感', () => {
  assert.equal(isResumableBuildRun({ status: 'PENDING', currentStage: 'PARSE' }), true)
})

test('pickResumableBuildRuns 按 updatedAt 倒序取前 N', () => {
  const result = pickResumableBuildRuns([
    { id: 1, status: 'success', currentStage: 'done', updatedAt: '2026-05-14T10:00:00' },
    { id: 2, status: 'running', currentStage: 'parse', updatedAt: '2026-05-14T11:00:00' },
    { id: 3, status: 'pending', currentStage: 'material_selection', updatedAt: '2026-05-14T12:00:00' },
    { id: 4, status: 'running', currentStage: 'index', updatedAt: '2026-05-14T13:00:00' },
  ], 2)
  assert.deepEqual(result.map((r) => r.id), [4, 3])
})

test('pickResumableBuildRuns 空输入返回空数组', () => {
  assert.deepEqual(pickResumableBuildRuns(), [])
  assert.deepEqual(pickResumableBuildRuns(null), [])
})

test('toResumeCard 生成跳转 URL 携带 buildRunId', () => {
  const card = toResumeCard(5, {
    id: 27,
    buildVersion: 'kb5-20260514120000',
    status: 'running',
    currentStage: 'parse',
    updatedAt: '2026-05-14T12:00:00',
  })
  assert.equal(card.id, 27)
  assert.equal(card.statusLabel, '运行中')
  assert.equal(card.stageLabel, '解析检查')
  assert.equal(card.to, '/app/knowledge-bases/5/build?buildRunId=27')
})

test('toResumeCard 缺 id 时 to 为空', () => {
  const card = toResumeCard(5, { buildVersion: '-' })
  assert.equal(card.to, '')
})

test('formatRelativeTime 分钟级展示', () => {
  const now = Date.parse('2026-05-14T12:00:00Z')
  assert.equal(formatRelativeTime(new Date(now - 30_000).toISOString(), now), '刚刚')
  assert.equal(formatRelativeTime(new Date(now - 600_000).toISOString(), now), '10 分钟前')
  assert.equal(formatRelativeTime(new Date(now - 7_200_000).toISOString(), now), '2 小时前')
})

test('formatRelativeTime 超过 24 小时回退到 yyyy-MM-dd HH:mm', () => {
  const now = Date.parse('2026-05-14T12:00:00Z')
  // 间隔 5 天，应回退到具体日期；具体小时按本地时区，仅断言前缀
  const value = formatRelativeTime(new Date(now - 5 * 86_400_000).toISOString(), now)
  assert.match(value, /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
})

test('formatRelativeTime 空值返回兜底文案', () => {
  assert.equal(formatRelativeTime(''), '时间未知')
  assert.equal(formatRelativeTime(null), '时间未知')
})
