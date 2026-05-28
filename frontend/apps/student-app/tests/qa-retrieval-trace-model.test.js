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
      summary: '仍在汇总 57 个课程报告批次，已处理约 8 秒；完成后会合并共同结论。',
      metrics: { reportGroupCount: 57, elapsedSeconds: 8 },
      eventSeq: 3,
    },
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在请求课程整体脉络，推荐全局综述模式。',
      metrics: { strategy: 'global' },
    },
    {
      type: 'map_started',
      mode: 'global',
      summary: '正在汇总 57 个课程报告批次，先提取与问题相关的要点。',
      metrics: { reportGroupCount: 57 },
      eventSeq: 2,
    },
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在请求课程整体脉络，推荐全局综述模式。',
      metrics: { strategy: 'global' },
      eventSeq: 1,
    },
    {
      type: 'map_running',
      mode: 'global',
      summary: '仍在汇总 57 个课程报告批次，已处理约 16 秒；完成后会合并共同结论。',
      metrics: { reportGroupCount: 57, elapsedSeconds: 16 },
    },
  ]

  const compacted = compactRetrievalTraceEvents(events)

  assert.deepEqual(compacted.map((event) => event.type), [
    'retrieval_started',
    'map_started',
    'map_running',
  ])
  assert.equal(compacted.at(-1).summary, '仍在汇总 57 个课程报告批次，已处理约 16 秒；完成后会合并共同结论。')
  assert.equal(latestRetrievalTraceEvent(events).type, 'map_running')
})

test('检索流摘要优先展示更靠后的 reduce 阶段', () => {
  const events = [
    {
      type: 'retrieval_started',
      summary: '正在请求课程整体脉络，推荐全局综述模式。',
    },
    {
      type: 'map_running',
      summary: '仍在汇总 57 个课程报告批次，已处理约 120 秒；完成后会合并共同结论。',
    },
    {
      type: 'reduce_started',
      summary: '正在综合课程报告要点，形成最终回答。',
    },
  ]

  assert.equal(latestRetrievalTraceEvent(events).type, 'reduce_started')
})

test('检索流合并时不会让大量心跳挤掉阶段锚点', () => {
  const currentEvents = [
    {
      type: 'retrieval_started',
      mode: 'global',
      summary: '正在请求课程整体脉络，推荐全局综述模式。',
      eventSeq: 1,
    },
    {
      type: 'map_started',
      mode: 'global',
      summary: '正在汇总 57 个课程报告批次，先提取与问题相关的要点。',
      eventSeq: 2,
    },
    ...Array.from({ length: 20 }, (_, index) => ({
      type: 'map_running',
      mode: 'global',
      summary: `仍在汇总 57 个课程报告批次，已处理约 ${(index + 1) * 8} 秒；完成后会合并共同结论。`,
      metrics: { reportGroupCount: 57, elapsedSeconds: (index + 1) * 8 },
      eventSeq: index + 3,
    })),
  ]

  const merged = mergeRetrievalTraceEvents(currentEvents, [{
    type: 'map_running',
    mode: 'global',
    summary: '仍在汇总 57 个课程报告批次，已处理约 168 秒；完成后会合并共同结论。',
    metrics: { reportGroupCount: 57, elapsedSeconds: 168 },
    eventSeq: 23,
  }])

  assert.deepEqual(merged.map((event) => event.type), [
    'retrieval_started',
    'map_started',
    'map_running',
  ])
  assert.equal(latestRetrievalTraceEvent(merged).summary, '仍在汇总 57 个课程报告批次，已处理约 168 秒；完成后会合并共同结论。')
})

test('global 报告批次依据使用学生可读标签和清洗后的标题摘要', () => {
  const event = {
    type: 'map_started',
    mode: 'global',
    summary: '正在汇总 57 个课程报告批次，先提取与问题相关的要点。',
    evidence: [
      {
        kind: 'report_group',
        title: '进程核心概念及其资源管理与同步生态体系',
        snippet: '本社区以操作系统核心实体“进程”为中心，构建了高度内聚的知识生态网络。',
      },
    ],
  }

  assert.equal(retrievalTraceEvidenceLabel(event, 'Global'), '课程报告批次 1 条')
  assert.equal(retrievalTraceEvidenceTitle(event.evidence[0]), '进程核心概念及其资源管理与同步生态体系')
  assert.equal(
    retrievalTraceEvidenceSnippet(event.evidence[0]),
    '本社区以操作系统核心实体“进程”为中心，构建了高度内聚的知识生态网络。',
  )
})
