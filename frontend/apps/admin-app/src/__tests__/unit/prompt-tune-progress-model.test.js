import test from 'node:test'
import assert from 'node:assert/strict'
import {
  resolveProgressPercentage,
  resolvePrimaryAction,
  resolveStageLabel,
  selectLatestLogTail,
} from '../../components/build-wizard/prompt-tune-progress-model.js'

test('resolveProgressPercentage success 总是 100', () => {
  assert.equal(resolveProgressPercentage('success', 'done'), 100)
  assert.equal(resolveProgressPercentage('success', null), 100)
})

test('resolveProgressPercentage failed/cancelled 为 0', () => {
  assert.equal(resolveProgressPercentage('failed', 'fetch_input'), 0)
  assert.equal(resolveProgressPercentage('cancelled', 'prompt_tune'), 0)
})

test('resolveProgressPercentage pending 为 5', () => {
  assert.equal(resolveProgressPercentage('pending', 'queued'), 5)
})

test('resolveProgressPercentage running 不同 stage 给不同档位', () => {
  assert.equal(resolveProgressPercentage('running', 'queued'), 30)
  assert.equal(resolveProgressPercentage('running', 'fetch_input'), 25)
  assert.equal(resolveProgressPercentage('running', 'prompt_tune'), 65)
  assert.equal(resolveProgressPercentage('running', 'done'), 95)
})

test('resolveStageLabel 已知阶段返回中文标签', () => {
  assert.equal(resolveStageLabel('queued'), '已入队，等待执行')
  assert.equal(resolveStageLabel('fetch_input'), '正在拉取课程资料')
  assert.equal(resolveStageLabel('prompt_tune'), '正在调用 GraphRAG 官方调优')
  assert.equal(resolveStageLabel('done'), '已完成')
})

test('resolveStageLabel 未知阶段原样返回', () => {
  assert.equal(resolveStageLabel('mystery'), 'mystery')
  assert.equal(resolveStageLabel(undefined), '')
})

test('selectLatestLogTail 末尾保留 6 行', () => {
  const log = Array.from({ length: 10 }, (_, i) => `line ${i}`).join('\n')
  const result = selectLatestLogTail(log)
  assert.equal(result.length, 6)
  assert.equal(result[0], 'line 4')
  assert.equal(result[5], 'line 9')
})

test('selectLatestLogTail 空字符串返回空数组', () => {
  assert.deepEqual(selectLatestLogTail(''), [])
  assert.deepEqual(selectLatestLogTail(null), [])
  assert.deepEqual(selectLatestLogTail(undefined), [])
})

test('selectLatestLogTail 过滤空行', () => {
  // 末 6 行：'b'、''、'c'、''、''、'd' → 过滤空后保留 3 行
  const log = 'a\nb\n\nc\n\n\nd'
  const result = selectLatestLogTail(log)
  assert.deepEqual(result, ['b', 'c', 'd'])
})

test('resolvePrimaryAction not_started 给开始调优', () => {
  assert.deepEqual(resolvePrimaryAction('not_started'), { label: '开始调优', kind: 'trigger' })
})

test('resolvePrimaryAction failed 给重试', () => {
  assert.deepEqual(resolvePrimaryAction('failed'), { label: '重试', kind: 'retry' })
})

test('resolvePrimaryAction success 给重新生成', () => {
  assert.deepEqual(resolvePrimaryAction('success'), { label: '重新生成', kind: 'regenerate' })
})

test('resolvePrimaryAction running/pending 不返回主按钮', () => {
  assert.equal(resolvePrimaryAction('running'), null)
  assert.equal(resolvePrimaryAction('pending'), null)
})
