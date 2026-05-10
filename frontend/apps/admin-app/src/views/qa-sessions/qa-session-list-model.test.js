import test from 'node:test'
import assert from 'node:assert/strict'

import {
  SESSION_TYPE_LABELS,
  resolveSessionStatusTone,
  resolveSessionAnomaly,
  mapSessionToCard,
  buildListParams,
} from './qa-session-list-model.js'

test('SESSION_TYPE_LABELS 将 smoke 文案清洗为"知识库验证"', () => {
  assert.equal(SESSION_TYPE_LABELS.smoke, '知识库验证')
  assert.equal(SESSION_TYPE_LABELS.formal, '正式问答')
})

test('resolveSessionStatusTone：异常优先返回 warning', () => {
  assert.deepEqual(
    resolveSessionStatusTone({ status: 'completed', hasAnomaly: true }),
    { tone: 'warning', label: '异常' },
  )
})

test('resolveSessionStatusTone：进行中 / 处理中 → running', () => {
  assert.deepEqual(resolveSessionStatusTone({ status: 'running' }), { tone: 'running', label: '进行中' })
  assert.deepEqual(resolveSessionStatusTone({ status: 'PROCESSING' }), { tone: 'running', label: '进行中' })
})

test('resolveSessionStatusTone：失败 / 完成 / 未知 三类兜底', () => {
  assert.deepEqual(resolveSessionStatusTone({ status: 'failed' }), { tone: 'danger', label: '失败' })
  assert.deepEqual(resolveSessionStatusTone({ status: 'error' }), { tone: 'danger', label: '失败' })
  assert.deepEqual(resolveSessionStatusTone({ status: 'completed' }), { tone: 'success', label: '完成' })
  assert.deepEqual(resolveSessionStatusTone({ status: 'custom' }), { tone: 'neutral', label: 'custom' })
  assert.deepEqual(resolveSessionStatusTone({}), { tone: 'neutral', label: '-' })
})

test('resolveSessionAnomaly 兼容 hasAnomaly / anomaly 两种字段', () => {
  assert.equal(resolveSessionAnomaly({ hasAnomaly: true }), true)
  assert.equal(resolveSessionAnomaly({ anomaly: true }), true)
  assert.equal(resolveSessionAnomaly({ hasAnomaly: false, anomaly: false }), false)
  assert.equal(resolveSessionAnomaly({}), false)
  assert.equal(resolveSessionAnomaly(null), false)
})

test('mapSessionToCard：正常会话 → 返回完整卡片字段', () => {
  const card = mapSessionToCard({
    id: 42,
    sessionCode: 'SES001',
    title: '动态规划问答',
    courseId: 'crs-ds',
    userDisplayName: '张同学',
    sessionType: 'formal',
    status: 'completed',
    messageCount: 8,
    lastMessageAt: '2026-05-10 14:00',
    createdAt: '2026-05-10 13:50',
  })

  assert.equal(card.id, 42)
  assert.equal(card.title, '动态规划问答')
  assert.equal(card.to, '/app/qa-sessions/42')
  assert.equal(card.description, '学员 张同学 · crs-ds')
  assert.equal(card.status, 'success')
  assert.equal(card.statusLabel, '完成')
  assert.equal(card.typeLabel, '正式问答')
  assert.equal(card.sessionType, 'formal')
  assert.equal(card.anomaly, false)
  assert.deepEqual(
    card.meta,
    [
      { label: '类型', value: '正式问答' },
      { label: '消息数', value: '8' },
    ],
  )
})

test('mapSessionToCard：smoke 类型显示为"知识库验证"', () => {
  const card = mapSessionToCard({
    id: 7,
    sessionType: 'smoke',
    title: '构建后验证',
    status: 'completed',
  })
  assert.equal(card.typeLabel, '知识库验证')
  assert.equal(card.meta[0].value, '知识库验证')
})

test('mapSessionToCard：缺学员 / 课程 → 使用兜底文案', () => {
  const card = mapSessionToCard({ id: 1, status: 'running', sessionType: 'formal' })
  assert.match(card.description, /学员 -/)
  assert.match(card.description, /未绑定课程/)
})

test('mapSessionToCard：异常会话 → anomaly=true 且 statusLabel="异常"', () => {
  const card = mapSessionToCard({
    id: 9,
    status: 'completed',
    hasAnomaly: true,
    anomalyReason: '模型响应超时',
    sessionType: 'formal',
  })
  assert.equal(card.anomaly, true)
  assert.equal(card.statusLabel, '异常')
  assert.equal(card.anomalyReason, '模型响应超时')
})

test('mapSessionToCard：id 缺失返回 null', () => {
  assert.equal(mapSessionToCard({}), null)
  assert.equal(mapSessionToCard(null), null)
})

test('buildListParams：空 query 使用默认 page/size 并跳过空字段', () => {
  const params = buildListParams({})
  assert.deepEqual(params, { page: 1, size: 20 })
})

test('buildListParams：归一 page / size 并接受字符串数字', () => {
  const params = buildListParams({ page: '3', pageSize: '50' })
  assert.equal(params.page, 3)
  assert.equal(params.size, 50)
})

test('buildListParams：只允许白名单 sessionType，其它剔除', () => {
  assert.equal(buildListParams({ sessionType: 'formal' }).sessionType, 'formal')
  assert.equal(buildListParams({ sessionType: 'smoke' }).sessionType, 'smoke')
  assert.equal(buildListParams({ sessionType: 'invalid' }).sessionType, undefined)
})

test('buildListParams：hasAnomaly 只识别 truthy 开关（1/true/yes/on）', () => {
  assert.equal(buildListParams({ hasAnomaly: '1' }).hasAnomaly, true)
  assert.equal(buildListParams({ hasAnomaly: 'true' }).hasAnomaly, true)
  assert.equal(buildListParams({ hasAnomaly: '0' }).hasAnomaly, undefined)
  assert.equal(buildListParams({ hasAnomaly: '' }).hasAnomaly, undefined)
})

test('buildListParams：keyword / courseId / 时间范围 trim 后只下发非空字段', () => {
  const params = buildListParams({
    keyword: '  动态规划  ',
    courseId: 'crs-os',
    knowledgeBaseId: '7',
    startAt: '2026-05-01',
    endAt: '',
  })
  assert.equal(params.keyword, '动态规划')
  assert.equal(params.courseId, 'crs-os')
  assert.equal(params.knowledgeBaseId, '7')
  assert.equal(params.startAt, '2026-05-01')
  assert.equal(params.endAt, undefined)
})
