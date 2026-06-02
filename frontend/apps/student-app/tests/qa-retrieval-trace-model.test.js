import test from 'node:test'
import assert from 'node:assert/strict'

import {
  compactRetrievalTraceEvents,
  latestRetrievalTraceEvent,
  mergeRetrievalTraceEvents,
  retrievalTraceEvidenceLabel,
  retrievalTraceEvidenceSnippet,
  retrievalTraceEvidenceTitle,
} from '../src/views/qa/qa-retrieval-trace-model.js'

test('检索流会去重 SSE 与快照中的同一事件并保持阶段顺序', () => {
  const events = [
    {
      type: 'map_running',
      mode: 'global',
      summary: '仍在逐组整理课程要点，已处理约 8 秒；完成后会合并成完整回答。',
      metrics: { reportGroupCount: 57, elapsedSeconds: 8 },
      eventSeq: 3,
    },
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在从整门课程中寻找和问题相关的章节主题。',
      metrics: { strategy: 'global' },
    },
    {
      type: 'map_started',
      mode: 'global',
      summary: '已找到 57 组相关课程内容，正在分别提炼要点。',
      metrics: { reportGroupCount: 57 },
      eventSeq: 2,
    },
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在从整门课程中寻找和问题相关的章节主题。',
      metrics: { strategy: 'global' },
      eventSeq: 1,
    },
    {
      type: 'map_running',
      mode: 'global',
      summary: '仍在逐组整理课程要点，已处理约 16 秒；完成后会合并成完整回答。',
      metrics: { reportGroupCount: 57, elapsedSeconds: 16 },
    },
  ]

  const compacted = compactRetrievalTraceEvents(events)

  assert.deepEqual(compacted.map((event) => event.type), [
    'retrieval_started',
    'map_started',
    'map_running',
  ])
  assert.equal(compacted.at(-1).summary, '仍在逐组整理课程要点，已处理约 16 秒；完成后会合并成完整回答。')
  assert.equal(latestRetrievalTraceEvent(events).type, 'map_running')
})

test('检索流摘要优先展示更靠后的 reduce 阶段', () => {
  const events = [
    {
      type: 'retrieval_started',
      summary: '正在从整门课程中寻找和问题相关的章节主题。',
    },
    {
      type: 'map_running',
      summary: '仍在逐组整理课程要点，已处理约 120 秒；完成后会合并成完整回答。',
    },
    {
      type: 'reduce_started',
      summary: '正在把分散的课程要点组织成完整回答。',
    },
  ]

  assert.equal(latestRetrievalTraceEvent(events).type, 'reduce_started')
})

test('检索流合并时不会让大量心跳挤掉阶段锚点', () => {
  const currentEvents = [
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在从整门课程中寻找和问题相关的章节主题。',
      eventSeq: 1,
    },
    {
      type: 'map_started',
      mode: 'global',
      summary: '已找到 57 组相关课程内容，正在分别提炼要点。',
      eventSeq: 2,
    },
    ...Array.from({ length: 20 }, (_, index) => ({
      type: 'map_running',
      mode: 'global',
      summary: `仍在逐组整理课程要点，已处理约 ${(index + 1) * 8} 秒；完成后会合并成完整回答。`,
      metrics: { reportGroupCount: 57, elapsedSeconds: (index + 1) * 8 },
      eventSeq: index + 3,
    })),
  ]

  const merged = mergeRetrievalTraceEvents(currentEvents, [{
    type: 'map_running',
    mode: 'global',
    summary: '仍在逐组整理课程要点，已处理约 168 秒；完成后会合并成完整回答。',
    metrics: { reportGroupCount: 57, elapsedSeconds: 168 },
    eventSeq: 23,
  }])

  assert.deepEqual(merged.map((event) => event.type), [
    'retrieval_started',
    'map_started',
    'map_running',
  ])
  assert.equal(latestRetrievalTraceEvent(merged).summary, '仍在逐组整理课程要点，已处理约 168 秒；完成后会合并成完整回答。')
})

test('global 检索流会把实时心跳与历史终态归并成稳定阶段列表', () => {
  const events = [
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在从整门课程中寻找和问题相关的章节主题。',
      eventSeq: 1,
    },
    {
      type: 'map_started',
      mode: 'global',
      summary: '已找到 57 组相关课程内容，正在分别提炼要点。',
      metrics: { reportGroupCount: 57 },
      eventSeq: 2,
    },
    {
      type: 'map_running',
      mode: 'global',
      summary: '仍在逐组整理课程要点，已处理约 16 秒；完成后会合并成完整回答。',
      eventSeq: 3,
    },
    {
      type: 'map_finished',
      mode: 'global',
      summary: '已整理 57 组课程要点，准备合并重复内容和共同结论。',
      eventSeq: 4,
    },
    {
      type: 'reduce_started',
      mode: 'global',
      summary: '正在把分散的课程要点组织成完整回答。',
      eventSeq: 5,
    },
    {
      type: 'reduce_running',
      mode: 'global',
      summary: '仍在整理完整回答，已处理约 32 秒；马上会开始显示正文。',
      eventSeq: 6,
    },
    {
      type: 'reduce_finished',
      mode: 'global',
      summary: '课程要点已整理完成，准备开始输出回答。',
      eventSeq: 7,
    },
  ]

  const compacted = compactRetrievalTraceEvents(events)

  assert.deepEqual(compacted.map((event) => event.type), [
    'retrieval_started',
    'map_started',
    'map_finished',
    'reduce_finished',
  ])
})

test('global 主题依据使用学生可读标签和清洗后的标题摘要', () => {
  const event = {
    type: 'map_started',
    mode: 'global',
    summary: '已找到 57 组相关课程内容，正在分别提炼要点。',
    metrics: { reportGroupCount: 57 },
    evidence: [
      {
        kind: 'report_group',
        title: '进程核心概念及其资源管理与同步生态体系',
        snippet: '本社区以操作系统核心实体“进程”为中心，构建了高度内聚的知识生态网络。',
      },
    ],
  }

  assert.equal(retrievalTraceEvidenceLabel(event, 'Global'), '精选展示 1 / 共 57 组')
  assert.equal(retrievalTraceEvidenceTitle(event.evidence[0]), '进程核心概念及其资源管理与同步生态体系')
  assert.equal(
    retrievalTraceEvidenceSnippet(event.evidence[0]),
    '本社区以操作系统核心实体“进程”为中心，构建了高度内聚的知识生态网络。',
  )
})

test('模型限流事件展示为中文模型服务阶段并折叠重复记录', () => {
  const events = [
    {
      type: 'model_rate_limit',
      mode: 'global',
      summary: 'openai.RateLimitError: 429 Too Many Requests retry-after: 7',
      metrics: { statusCode: 429, retryAfterSeconds: 7 },
      evidence: [{ kind: 'stderr', snippet: 'raw provider log' }],
      eventSeq: 8,
    },
    {
      type: 'model_rate_limit',
      mode: 'global',
      summary: 'second raw stderr should not appear',
      metrics: { statusCode: 429, retryAfterSeconds: 8 },
      eventSeq: 9,
    },
    {
      type: 'model_rate_limit_failed',
      mode: 'global',
      summary: 'insufficient_quota: exceeded monthly quota',
      metrics: { reasonType: 'rate_limit' },
      eventSeq: 10,
    },
  ]

  const compacted = compactRetrievalTraceEvents(events)

  assert.equal(compacted.length, 1)
  assert.equal(compacted[0].type, 'model_rate_limit_failed')
  assert.equal(compacted[0].summary, '模型服务持续繁忙，本次课程问答未能完成。')
  assert.deepEqual(compacted[0].evidence, [])
  assert.equal(retrievalTraceEvidenceLabel(compacted[0], 'Global'), '模型服务')
  assert.equal(latestRetrievalTraceEvent(events).summary, '模型服务持续繁忙，本次课程问答未能完成。')
})
