import test from 'node:test'
import assert from 'node:assert/strict'

import {
  Check,
  Hammer,
  RefreshCw,
  WandSparkles,
} from 'lucide-vue-next'

import {
  resolveBuildPrimaryActionIcon,
  resolveBuildStepIndexLabel,
  resolveBuildSummaryChips,
} from './build-wizard-form-model.js'

const STEPS = [
  { key: 'material' },
  { key: 'parse' },
  { key: 'export' },
  { key: 'prompt' },
  { key: 'index' },
  { key: 'qa_check' },
]

test('material step chips 显示已选资料和课程资料总数', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: {
      selection: { materialIds: [1, 2, 3] },
      materials: { items: [{}, {}, {}, {}, {}] },
    },
  })

  assert.deepEqual(chips, [
    { label: '已选资料', value: '3 个', tone: 'ok' },
    { label: '课程资料', value: '5 个', tone: 'info' },
  ])
})

test('material step 未选资料时 tone 为 warn', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: { selection: { materialIds: [] }, materials: { items: [] } },
  })

  assert.equal(chips[0].tone, 'warn')
})

test('material step 字符串 materialIds 不按字符串长度统计', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: {
      selection: { materialIds: '12' },
      materials: { items: [] },
    },
  })

  assert.deepEqual(chips[0], { label: '已选资料', value: '0 个', tone: 'warn' })
})

test('material step 字符串 materials.items 不按字符串长度统计', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: {
      selection: { materialIds: [] },
      materials: { items: 'abc' },
    },
  })

  assert.deepEqual(chips[1], { label: '课程资料', value: '0 个', tone: 'info' })
})

test('parse step chips 显示解析完成统计', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1, 2] },
      parseTasks: { items: [{ status: 'done' }, { status: 'done' }, { status: 'pending' }] },
    },
  })

  assert.equal(chips[1].label, '解析完成')
  assert.equal(chips[1].value, '2/3')
  assert.equal(chips[1].tone, 'info')
})

test('parse step 全部完成时 tone 为 ok', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1] },
      parseTasks: { items: [{ status: 'done' }, { status: 'done' }] },
    },
  })

  assert.equal(chips[1].tone, 'ok')
})

test('parse step 无解析任务时解析完成 tone 为 info', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1] },
      parseTasks: { items: [] },
    },
  })

  assert.deepEqual(chips[1], { label: '解析完成', value: '0/0', tone: 'info' })
})

test('parse step 字符串 parseTasks.items 不按字符串长度统计', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1] },
      parseTasks: { items: 'abc' },
    },
  })

  assert.deepEqual(chips[1], { label: '解析完成', value: '0/0', tone: 'info' })
})

test('export step chips 显示已导出和缺失产物', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'export',
    blocks: { exportArtifacts: { summary: { completeCount: 4, missingCount: 1 } } },
  })

  assert.deepEqual(chips, [
    { label: '已导出', value: '4 个', tone: 'ok' },
    { label: '缺失产物', value: '1 个', tone: 'warn' },
  ])
})

test('export step 无缺失时缺失产物 tone 为 ok', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'export',
    blocks: { exportArtifacts: { summary: { completeCount: 4, missingCount: 0 } } },
  })

  assert.equal(chips[1].tone, 'ok')
})

test('index 与 qa_check step chips 反映 indexAvailability', () => {
  const ready = resolveBuildSummaryChips({
    activeKey: 'index',
    blocks: { indexAvailability: { availability: 'available' } },
  })
  assert.deepEqual(ready, [{ label: '可用索引', value: '已就绪', tone: 'ok' }])

  const missing = resolveBuildSummaryChips({
    activeKey: 'qa_check',
    blocks: { indexAvailability: { availability: 'missing' } },
  })
  assert.deepEqual(missing, [{ label: '可用索引', value: '暂无', tone: 'info' }])
})

test('indexAvailability 支持 available 布尔兼容字段', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'index',
    blocks: { indexAvailability: { available: true } },
  })

  assert.deepEqual(chips, [{ label: '可用索引', value: '已就绪', tone: 'ok' }])
})

test('未知 activeKey 退化为单条已选资料 chip', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'unknown',
    blocks: { selection: { materialIds: [1, 2] } },
  })

  assert.deepEqual(chips, [{ label: '已选资料', value: '2 个', tone: 'ok' }])
})

test('resolveBuildPrimaryActionIcon 按 operationKey 匹配', () => {
  assert.equal(resolveBuildPrimaryActionIcon('qa-smoke'), WandSparkles)
  assert.equal(resolveBuildPrimaryActionIcon('parse-refresh'), RefreshCw)
  assert.equal(resolveBuildPrimaryActionIcon('material-confirm'), Check)
  assert.equal(resolveBuildPrimaryActionIcon('index-build'), Hammer)
  assert.equal(resolveBuildPrimaryActionIcon(undefined), Hammer)
})

test('resolveBuildStepIndexLabel 返回零填充 2 位字符串', () => {
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'material'), '01')
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'qa_check'), '06')
})

test('resolveBuildStepIndexLabel 未找到时回落到 01', () => {
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'unknown'), '01')
  assert.equal(resolveBuildStepIndexLabel([], 'material'), '01')
  assert.equal(resolveBuildStepIndexLabel(null, 'material'), '01')
})
