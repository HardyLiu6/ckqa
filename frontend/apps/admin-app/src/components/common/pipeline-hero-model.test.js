import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PIPELINE_STAGES,
  resolveStageMetric,
  isStageActive,
  buildPipelineNavTarget,
} from './pipeline-hero-model.js'

const SUMMARY = {
  courseCount: 12,
  materialCount: 428,
  materialReadyCount: 412,
  materialPendingCount: 16,
  knowledgeBaseCount: 9,
  knowledgeBaseRunningCount: 3,
  knowledgeBaseRunningPercents: [65, 32],
  activeKbCount: 1,
  activeKbVersion: 'v3',
  qaSessionCount: 1234,
  qaResponseTimeMs: 312,
  activeKey: 'knowledgeBases',
}

test('PIPELINE_STAGES 暴露 5 段且顺序固定', () => {
  assert.deepEqual(
    PIPELINE_STAGES.map((s) => s.key),
    ['courses', 'materials', 'knowledgeBases', 'activation', 'qa'],
  )
})

test('resolveStageMetric 课程段返回总数和待解析数', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '12',
    secondary: '',
    runningCount: 0,
  })
})

test('resolveStageMetric 资料段返回 ready/total + pending', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'materials')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '428/412',
    secondary: '16 待解析',
    runningCount: 0,
  })
})

test('resolveStageMetric 知识库段附运行中和进度', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '3/9 构建中',
    secondary: '65% / 32%',
    runningCount: 3,
  })
})

test('resolveStageMetric 激活段返回数量与最新版本', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'activation')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '1',
    secondary: '最新 v3',
    runningCount: 0,
  })
})

test('resolveStageMetric 问答段返回累计数与响应时间', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'qa')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '1.2k',
    secondary: '响应 312ms（高负载下）',
    runningCount: 0,
  })
})

test('resolveStageMetric 容忍空 summary', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.deepEqual(resolveStageMetric(stage, null), {
    primary: '—',
    secondary: '',
    runningCount: 0,
  })
})

test('isStageActive 当段命中 activeKey 时为 true', () => {
  const kb = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.equal(isStageActive(kb, SUMMARY), true)
  const courses = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.equal(isStageActive(courses, SUMMARY), false)
})

test('isStageActive 当 runningCount > 0 也激活', () => {
  const kb = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.equal(isStageActive(kb, { knowledgeBaseRunningCount: 1 }), true)
})

test('buildPipelineNavTarget 拼接 status=running + courseId', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  const target = buildPipelineNavTarget(stage, { courseId: 'os-2026' })
  assert.equal(target.path, '/app/knowledge-bases')
  assert.deepEqual(target.query, { status: 'running', courseId: 'os-2026' })
})

test('buildPipelineNavTarget 在范围全平台时省略 courseId', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'qa')
  const target = buildPipelineNavTarget(stage, {})
  assert.deepEqual(target, { path: '/app/qa-sessions', query: {} })
})
