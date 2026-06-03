import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildRetrievalTimeline,
  buildRetrievalTraceSummary,
  compactRetrievalTraceEvents,
  latestRetrievalTraceEvent,
  mergeRetrievalTraceEvents,
  retrievalTraceEvidenceLabel,
  retrievalTraceEvidenceSnippet,
  retrievalTraceEvidenceTitle,
} from '../src/views/qa/qa-retrieval-trace-model.js'

const GLOBAL_EVENTS = [
  {
    type: 'retrieval_started',
    mode: 'global',
    summary: '正在从整门课程中寻找和问题相关的章节主题。',
    eventSeq: 1,
    receivedAtMs: 1_000_000,
  },
  {
    type: 'map_started',
    mode: 'global',
    summary: '已找到 57 组相关课程内容，正在分别提炼要点。',
    metrics: { reportGroupCount: 57 },
    evidence: Array.from({ length: 5 }, (_, index) => ({
      kind: 'report_group',
      title: `课程主题 ${index + 1}`,
      snippet: `课程主题摘要 ${index + 1}`,
    })),
    eventSeq: 2,
    receivedAtMs: 1_004_000,
  },
  {
    type: 'map_running',
    mode: 'global',
    summary: '仍在逐组整理课程要点，已处理约 999 秒；完成后会合并成完整回答。',
    metrics: { reportGroupCount: 57, elapsedSeconds: 999 },
    eventSeq: 3,
    receivedAtMs: 1_008_000,
  },
  {
    type: 'map_running',
    mode: 'global',
    summary: '仍在逐组整理课程要点，已处理约 1008 秒；完成后会合并成完整回答。',
    metrics: { reportGroupCount: 57, elapsedSeconds: 1008 },
    eventSeq: 4,
    receivedAtMs: 1_012_000,
  },
]

const DRIFT_EVENTS = [
  {
    type: 'retrieval_started',
    mode: 'drift',
    summary: '正在沿课程报告和概念线索展开追问式检索。',
    eventSeq: 1,
    receivedAtMs: 2_000_000,
  },
  {
    type: 'context_selected',
    mode: 'drift',
    summary: '已选取 2 份课程报告作为回答依据。',
    metrics: { reportCount: 2 },
    evidence: [
      {
        kind: 'report_group',
        title: '分页与分段的内存管理线索',
        snippet: '围绕分页、分段、地址变换和内存保护组织课程依据。',
      },
    ],
    eventSeq: 2,
    receivedAtMs: 2_004_000,
  },
  {
    type: 'reduce_started',
    mode: 'drift',
    summary: '正在把分散的课程要点组织成完整回答。',
    evidence: [
      {
        title: 'context',
        snippet: '1\n2',
      },
    ],
    eventSeq: 3,
    receivedAtMs: 2_012_000,
  },
  {
    type: 'reduce_finished',
    mode: 'drift',
    summary: '课程要点已整理完成，准备开始输出回答。',
    eventSeq: 4,
    receivedAtMs: 2_024_000,
  },
]

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

test('时间线模型把 global 检索事件归并成稳定阶段', () => {
  const timeline = buildRetrievalTimeline(GLOBAL_EVENTS, {
    live: true,
    nowMs: 1_024_000,
  })

  assert.deepEqual(timeline.items.map((item) => item.title), [
    '检索课程范围',
    '筛选依据',
    '整理要点',
    '组织回答',
  ])
  assert.deepEqual(timeline.items.map((item) => item.status), [
    'done',
    'done',
    'active',
    'pending',
  ])
  assert.equal(timeline.items[2].summary, '仍在逐组整理课程要点，已处理约 1008 秒；完成后会合并成完整回答。')
  assert.equal(timeline.items[1].evidenceLabel, '精选展示 3 / 共 57 组')
  assert.equal(timeline.items[1].evidence.length, 3)
})

test('实时折叠摘要使用前端本地连续计时而不是后端心跳秒数', () => {
  const summary = buildRetrievalTraceSummary(GLOBAL_EVENTS, {
    live: true,
    nowMs: 1_024_000,
  })

  assert.equal(summary.countText, '3/4 阶段')
  assert.equal(summary.timeText, '已用时 00:24')
  assert.equal(summary.currentText, '正在整理要点')
  assert.equal(summary.evidenceText, '精选展示 3 / 共 57 组')
  assert.equal(summary.text, '已用时 00:24 · 正在整理要点 · 精选展示 3 / 共 57 组')
})

test('实时和历史使用同一组阶段归并，只改变耗时展示方式', () => {
  const liveTimeline = buildRetrievalTimeline(GLOBAL_EVENTS, {
    live: true,
    nowMs: 1_024_000,
  })
  const historyTimeline = buildRetrievalTimeline(GLOBAL_EVENTS, {
    live: false,
    taskFinishedAtMs: 1_024_000,
  })
  const historySummary = buildRetrievalTraceSummary(GLOBAL_EVENTS, {
    live: false,
    taskFinishedAtMs: 1_024_000,
  })

  assert.deepEqual(
    historyTimeline.items.map((item) => [item.key, item.title, item.summary]),
    liveTimeline.items.map((item) => [item.key, item.title, item.summary]),
  )
  assert.equal(historySummary.timeText, '耗时约 24 秒')
  assert.equal(historySummary.text, '耗时约 24 秒 · 正在整理要点 · 精选展示 3 / 共 57 组')
})

test('已有最终回答时即使缺少回答阶段事件也显示完整完成态', () => {
  const timeline = buildRetrievalTimeline(GLOBAL_EVENTS, {
    live: false,
    hasAnswer: true,
    taskFinishedAtMs: 1_024_000,
  })
  const summary = buildRetrievalTraceSummary(GLOBAL_EVENTS, {
    live: false,
    hasAnswer: true,
    taskFinishedAtMs: 1_024_000,
  })

  assert.deepEqual(timeline.items.map((item) => item.status), [
    'done',
    'done',
    'done',
    'done',
  ])
  assert.equal(timeline.items.at(-1).title, '组织回答')
  assert.equal(timeline.items.at(-1).summary, '回答已生成。')
  assert.equal(summary.countText, '4/4 阶段')
  assert.equal(summary.currentText, '回答已完成')
  assert.equal(summary.text, '耗时约 24 秒 · 回答已完成 · 精选展示 3 / 共 57 组')
})

test('已有最终回答和参考来源时折叠摘要优先展示最终来源总数', () => {
  const summary = buildRetrievalTraceSummary(GLOBAL_EVENTS, {
    live: false,
    hasAnswer: true,
    sourceCount: 28,
    taskFinishedAtMs: 1_024_000,
  })

  assert.equal(summary.evidenceText, '参考来源 28 条')
  assert.equal(summary.text, '耗时约 24 秒 · 回答已完成 · 参考来源 28 条')
})

test('drift 检索流使用追问式检索语义而不是 global map/reduce 文案', () => {
  const timeline = buildRetrievalTimeline(DRIFT_EVENTS, {
    live: false,
    hasAnswer: true,
    sourceCount: 3,
    taskFinishedAtMs: 2_024_000,
  })
  const summary = buildRetrievalTraceSummary(timeline, {
    hasAnswer: true,
    sourceCount: 3,
  })

  assert.deepEqual(timeline.items.map((item) => item.title), [
    '定位起点',
    '追问扩展',
    '汇总回答',
  ])
  assert.deepEqual(timeline.items.map((item) => item.status), ['done', 'done', 'done'])
  assert.equal(timeline.items[0].summary, '正在定位课程报告、概念和片段线索，准备展开追问式检索。')
  assert.equal(timeline.items[1].summary, '已围绕问题选取 2 份课程报告，继续扩展相关依据。')
  assert.equal(timeline.items[2].summary, '回答已生成。')
  assert.deepEqual(timeline.items[2].evidence, [])
  assert.equal(summary.countText, '3/3 阶段')
  assert.equal(summary.currentText, '回答已完成')
  assert.equal(summary.evidenceText, '参考来源 3 条')
})

test('drift 追问结果证据使用学生可读标签', () => {
  const event = {
    type: 'reduce_started',
    mode: 'drift',
    summary: '已完成追问扩展，正在汇总 2 条中间结论。',
    evidence: [
      {
        kind: 'drift_answer',
        title: '抢占式调度的优缺点',
        snippet: '抢占式调度能提高响应性，但会增加调度开销。',
      },
      {
        kind: 'drift_answer',
        title: '非抢占式调度的优缺点',
        snippet: '非抢占式调度实现简单，但交互响应较差。',
      },
    ],
  }

  assert.equal(retrievalTraceEvidenceLabel(event, 'Drift'), '追问结果 2 条')
})

test('drift 追问结果归属追问扩展阶段且隐藏没有答案的追问节点', () => {
  const timeline = buildRetrievalTimeline([
    {
      type: 'retrieval_started',
      mode: 'drift',
      summary: '正在沿课程报告和概念线索展开追问式检索。',
      eventSeq: 1,
    },
    {
      type: 'reduce_started',
      mode: 'drift',
      summary: '已完成追问扩展，正在汇总 3 条中间结论。',
      eventSeq: 2,
      evidence: [
        {
          kind: 'drift_answer',
          title: '抢占式调度的优缺点',
          snippet: '抢占式调度响应快，但调度开销较高。',
        },
        {
          kind: 'drift_answer',
          title: '非抢占式调度的优缺点',
          snippet: '非抢占式调度实现简单，但交互响应较差。',
        },
        {
          kind: 'drift_answer',
          title: '哪些场景会选择非抢占式调度？',
          snippet: '',
        },
      ],
    },
  ], {
    live: true,
  })

  assert.equal(timeline.items[1].title, '追问扩展')
  assert.equal(timeline.items[1].evidenceLabel, '追问结果 2 条')
  assert.deepEqual(timeline.items[1].evidence.map((item) => item.title), [
    '抢占式调度的优缺点',
    '非抢占式调度的优缺点',
  ])
  assert.equal(timeline.items[2].title, '汇总回答')
  assert.equal(timeline.items[2].status, 'active')
  assert.deepEqual(timeline.items[2].evidence, [])
})

test('drift 真实后端追问结果无 kind 时仍归属追问扩展并隐藏空答案', () => {
  const timeline = buildRetrievalTimeline([
    {
      type: 'retrieval_started',
      mode: 'drift',
      summary: '正在沿课程报告和概念线索展开追问式检索。',
      eventSeq: 1,
    },
    {
      type: 'reduce_started',
      mode: 'drift',
      summary: '已完成追问扩展，正在汇总 5 条中间结论。',
      eventSeq: 2,
      evidence: [
        {
          title: '抢占式调度和非抢占式调度各有什么优缺点？',
          snippet: '抢占式调度能提高响应性，但会增加上下文切换开销。',
        },
        {
          title: '什么是多级反馈队列调度？',
          snippet: '',
        },
        {
          title: '非抢占式调度在哪些场景中仍然适用？',
        },
      ],
    },
  ], {
    live: true,
  })

  assert.equal(timeline.items[1].title, '追问扩展')
  assert.equal(timeline.items[1].evidenceLabel, '追问结果 1 条')
  assert.deepEqual(timeline.items[1].evidence.map((item) => item.title), [
    '抢占式调度和非抢占式调度各有什么优缺点？',
  ])
  assert.equal(timeline.items[1].evidence[0].kind, 'drift_answer')
  assert.deepEqual(timeline.items[2].evidence, [])
})

test('drift 追问扩展显示追问线索总数和已回答追问数量', () => {
  const timeline = buildRetrievalTimeline([
    {
      type: 'retrieval_started',
      mode: 'drift',
      summary: '正在沿课程报告和概念线索展开追问式检索。',
      eventSeq: 1,
    },
    {
      type: 'reduce_started',
      mode: 'drift',
      summary: '已生成 8 条追问线索，其中 1 条已有追问回答，正在汇总回答。',
      metrics: {
        driftNodeCount: 8,
        answeredNodeCount: 1,
        pendingNodeCount: 7,
      },
      eventSeq: 2,
      evidence: [
        {
          kind: 'drift_answer',
          title: '抢占式调度和非抢占式调度各有什么优缺点？',
          snippet: '抢占式调度响应更快，非抢占式调度实现更简单。',
        },
      ],
    },
  ], {
    live: true,
  })

  assert.equal(timeline.items[1].title, '追问扩展')
  assert.equal(timeline.items[1].evidenceLabel, '已回答追问 1 / 追问线索 8 条')
  assert.equal(timeline.items[1].summary, '已生成 8 条追问线索，其中 1 条已有追问回答，可供汇总。')
})

test('任务失败时检索过程不再显示运行中阶段', () => {
  const timeline = buildRetrievalTimeline(GLOBAL_EVENTS, {
    live: true,
    taskStatus: 'failed',
    nowMs: 1_624_000,
  })
  const summary = buildRetrievalTraceSummary(timeline)

  assert.equal(timeline.live, false)
  assert.equal(timeline.failed, true)
  assert.deepEqual(timeline.items.map((item) => item.status), [
    'done',
    'done',
    'failed',
    'pending',
  ])
  assert.equal(summary.currentText, '问答已失败')
  assert.doesNotMatch(summary.text, /正在整理要点/)
  assert.doesNotMatch(summary.text, /已用时/)
})

test('模型限流在时间线中显示为中文脱敏的模型服务阶段', () => {
  const timeline = buildRetrievalTimeline([
    ...GLOBAL_EVENTS,
    {
      type: 'model_rate_limit_failed',
      mode: 'global',
      summary: 'openai.RateLimitError: 429 Too Many Requests',
      metrics: { statusCode: 429 },
      evidence: [{ kind: 'stderr', snippet: 'raw provider log' }],
      eventSeq: 5,
      receivedAtMs: 1_016_000,
    },
  ], {
    live: true,
    nowMs: 1_024_000,
  })
  const summary = buildRetrievalTraceSummary(timeline.events, {
    live: true,
    nowMs: 1_024_000,
  })

  assert.equal(timeline.items.at(-1).title, '模型服务等待')
  assert.equal(timeline.items.at(-1).status, 'failed')
  assert.equal(timeline.items.at(-1).summary, '模型服务持续繁忙，本次课程问答未能完成。')
  assert.deepEqual(timeline.items.at(-1).evidence, [])
  assert.equal(summary.currentText, '模型服务繁忙，正在等待重试')
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

  assert.ok(merged.length > 3)
  assert.equal(merged[0].type, 'retrieval_started')
  assert.equal(latestRetrievalTraceEvent(merged).summary, '仍在逐组整理课程要点，已处理约 168 秒；完成后会合并成完整回答。')
  assert.deepEqual(
    buildRetrievalTimeline(merged).items
      .filter((item) => item.status !== 'pending')
      .map((item) => item.key),
    ['retrieval', 'evidence', 'map'],
  )
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

test('map 阶段依据会把 GraphRAG 原始 map_result 清洗成课程要点', () => {
  const evidence = {
    kind: 'map_result',
    title: 'map_result',
    snippet: "map_result: [{'answer': '多级索引组织是文件物理结构的核心方案，通过第一级索引表指向第二级索引块，再由第二级索引块指向实际数据块。', 'score': 85}]",
  }

  assert.equal(retrievalTraceEvidenceTitle(evidence), '多级索引组织是文件物理结构的核心方案')
  assert.equal(
    retrievalTraceEvidenceSnippet(evidence),
    '通过第一级索引表指向第二级索引块，再由第二级索引块指向实际数据块。',
  )
})

test('未知类型过程依据只显示样例数量而不是依据总数', () => {
  const event = {
    type: 'context_selected',
    mode: 'global',
    summary: '已选取课程依据。',
    evidence: Array.from({ length: 5 }, (_, index) => ({
      kind: index % 2 === 0 ? 'source' : '',
      title: `过程依据 ${index + 1}`,
      snippet: `过程依据摘要 ${index + 1}`,
    })),
  }

  assert.equal(retrievalTraceEvidenceLabel(event, 'Global'), '展示样例 3 条')
})

test('local 关系依据标签使用概念关系语义和真实总数', () => {
  const event = {
    type: 'context_selected',
    mode: 'local',
    summary: '已选取 8 条概念关系作为上下文。',
    metrics: {
      relationshipCount: 8,
    },
    evidence: Array.from({ length: 5 }, (_, index) => ({
      kind: 'relationship',
      title: `${2493 + index}`,
      snippet: `银行家算法关系依据 ${index + 1}`,
    })),
  }

  assert.equal(retrievalTraceEvidenceLabel(event, 'Local'), '概念关系 3 / 共 8 条')
})

test('hybrid 时间线展示低层 BM25 证据和融合上下文阶段', () => {
  const timeline = buildRetrievalTimeline([
    {
      type: 'retrieval_started',
      mode: 'hybrid_v0',
      summary: '正在启动混合检索。',
      metrics: { strategy: 'hybrid_v0' },
      evidence: [],
    },
    {
      type: 'hybrid_low_evidence_selected',
      mode: 'hybrid_v0',
      summary: '低层 BM25 已召回 8 个候选课程片段，准备注入 GraphRAG Basic 做上下文融合。',
      metrics: { bm25EvidenceCount: 8, textUnitCount: 8 },
      evidence: Array.from({ length: 5 }, (_, index) => ({
        kind: 'bm25',
        title: `BM25 证据 ${index + 1}`,
        snippet: 'SJF 和 SRTN 的教材片段。',
      })),
    },
    {
      type: 'context_selected',
      mode: 'hybrid_v0',
      summary: 'GraphRAG Basic 已基于混合证据构建融合上下文。',
      metrics: { textUnitCount: 3, fusionStage: 'basic_context' },
      evidence: [
        {
          kind: 'text_unit',
          title: '操作系统教材',
          snippet: 'SRTN 是抢占式短作业优先。',
        },
      ],
    },
    {
      type: 'answer_running',
      mode: 'hybrid_v0',
      summary: '仍在基于混合证据组织回答。',
      metrics: { elapsedSeconds: 8 },
      evidence: [],
    },
  ])

  assert.deepEqual(timeline.items.map((item) => item.title), [
    '检索课程范围',
    '召回混合证据',
    '融合课程上下文',
    '融合回答',
  ])
  assert.equal(timeline.totalCount, 4)
  assert.equal(timeline.items[1].evidenceLabel, 'BM25 片段 3 / 共 8 条')
  assert.equal(timeline.items[2].summary, 'GraphRAG Basic 已基于混合证据构建融合上下文。')
})

test('hybrid 时间线优先使用事件真实模式而不是展示标签', () => {
  const timeline = buildRetrievalTimeline([
    {
      type: 'retrieval_started',
      mode: 'hybrid_v0',
      summary: '正在检索混合证据。',
      metrics: { strategy: 'hybrid_v0' },
    },
    {
      type: 'hybrid_low_evidence_selected',
      mode: 'hybrid_v0',
      summary: '低层 BM25 已召回 8 个候选课程片段，准备注入 GraphRAG Basic 做上下文融合。',
      metrics: { bm25EvidenceCount: 8 },
      evidence: [
        {
          kind: 'bm25',
          title: 'SJF 教材片段',
          snippet: '短作业优先调度算法。',
        },
      ],
    },
    {
      type: 'context_selected',
      mode: 'hybrid_v0',
      summary: 'GraphRAG Basic 已基于混合证据构建融合上下文。',
      metrics: { fusionStage: 'basic_context', textUnitCount: 3 },
    },
  ], {
    mode: 'Hybrid',
    taskStatus: 'success',
    hasAnswer: true,
  })

  assert.deepEqual(timeline.items.map((item) => item.title), [
    '检索课程范围',
    '召回混合证据',
    '融合课程上下文',
    '融合回答',
  ])
  assert.equal(timeline.totalCount, 4)
  assert.equal(timeline.reachedCount, 4)
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
