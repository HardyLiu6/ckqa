import test from 'node:test'
import assert from 'node:assert/strict'
import {
  STATUS_LABELS,
  STAGE_LABELS,
  QA_STATUS_LABELS,
  buildBuildRunListParams,
  formatBuildVersion,
  mapBuildRunRow,
} from '../../views/pages/build-run-list-model.js'

test('buildBuildRunListParams 默认 page=1 size=20', () => {
  assert.deepEqual(buildBuildRunListParams({}), { page: 1, size: 20, status: '' })
})

test('buildBuildRunListParams 传入 page 字符串能正确解析', () => {
  assert.deepEqual(buildBuildRunListParams({ page: '3' }), { page: 3, size: 20, status: '' })
})

test('buildBuildRunListParams page 非法值回退 1', () => {
  assert.deepEqual(buildBuildRunListParams({ page: '-2' }), { page: 1, size: 20, status: '' })
  assert.deepEqual(buildBuildRunListParams({ page: 'abc' }), { page: 1, size: 20, status: '' })
})

test('buildBuildRunListParams status 透传', () => {
  assert.deepEqual(buildBuildRunListParams({ status: 'failed' }).status, 'failed')
})

test('STATUS_LABELS 覆盖五个有效枚举（interrupted 因后端未使用已从前端移除）', () => {
  assert.equal(STATUS_LABELS.pending, '待开始')
  assert.equal(STATUS_LABELS.running, '运行中')
  assert.equal(STATUS_LABELS.success, '已完成')
  assert.equal(STATUS_LABELS.failed, '失败')
  assert.equal(STATUS_LABELS.archived, '已归档')
  assert.equal(STATUS_LABELS.interrupted, undefined)
})

test('STAGE_LABELS 覆盖七个阶段', () => {
  assert.equal(STAGE_LABELS.material_selection, '资料选择')
  assert.equal(STAGE_LABELS.parse, '解析检查')
  assert.equal(STAGE_LABELS.graph_input_export, '图谱输入')
  assert.equal(STAGE_LABELS.prompt, '提示词')
  assert.equal(STAGE_LABELS.index, '索引构建')
  assert.equal(STAGE_LABELS.qa_smoke, 'QA 冒烟')
  assert.equal(STAGE_LABELS.done, '已完成')
})

test('QA_STATUS_LABELS 覆盖五个状态', () => {
  assert.equal(QA_STATUS_LABELS.pending, '待执行')
  assert.equal(QA_STATUS_LABELS.running, '运行中')
  assert.equal(QA_STATUS_LABELS.success, '通过')
  assert.equal(QA_STATUS_LABELS.failed, '失败')
  assert.equal(QA_STATUS_LABELS.skipped, '已跳过')
})

test('formatBuildVersion 截短长版本号', () => {
  assert.equal(formatBuildVersion('kb5-20260505123456789-abcd'), 'kb5-20260505123456789-abcd')
  assert.equal(formatBuildVersion(null), '-')
  assert.equal(formatBuildVersion(''), '-')
})

test('mapBuildRunRow 输出 7 列', () => {
  const row = mapBuildRunRow(5, {
    id: 27,
    knowledgeBaseId: 5,
    courseId: 'os',
    buildVersion: 'kb5-20260505000000000-abcd',
    status: 'success',
    currentStage: 'done',
    qaStatus: 'success',
    activeIndexRunId: 99,
    createdAt: '2026-05-05T00:00:00',
    updatedAt: '2026-05-05T00:30:00',
  })
  assert.equal(row.id, 27)
  assert.equal(row.cells.length, 7)
  assert.equal(row.cells[0], 'kb5-20260505000000000-abcd')
  assert.deepEqual(row.cells[1], { kind: 'status', status: 'success', label: '已完成', filterValue: 'success' })
  assert.equal(row.cells[2], '已完成')
  assert.deepEqual(row.cells[3], { kind: 'status', status: 'success', label: '通过', filterValue: 'success' })
  assert.equal(row.cells[4], '#99')
  assert.equal(row.cells[5], '2026-05-05T00:00:00')
  assert.equal(row.cells[6], '2026-05-05T00:30:00')
})

test('mapBuildRunRow 行动作包含打开向导和删除', () => {
  const successRow = mapBuildRunRow(5, { id: 27, status: 'success', activeIndexRunId: 99 })
  const actionKeys = successRow.actions.map((a) => a.key ?? a.label)
  assert.ok(actionKeys.includes('打开向导'))
  assert.ok(actionKeys.includes('delete-build-run'))
})

test('mapBuildRunRow running 状态也有删除按钮', () => {
  const runningRow = mapBuildRunRow(5, { id: 28, status: 'running' })
  const runningKeys = runningRow.actions.map((a) => a.key ?? a.label)
  assert.ok(runningKeys.includes('打开向导'))
  assert.ok(runningKeys.includes('delete-build-run'))
})

test('mapBuildRunRow 无 id 时无动作', () => {
  const row = mapBuildRunRow(5, { status: 'success' })
  assert.equal(row.actions.length, 0)
})
