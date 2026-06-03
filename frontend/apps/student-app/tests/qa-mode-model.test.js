import test from 'node:test'
import assert from 'node:assert/strict'

import {
  BACKEND_QA_MODES,
  QA_MODE_OPTIONS,
  SMART_QA_MODE,
  loadHybridBetaPreference,
  resolveQaMode,
  resolveQaModeRecommendation,
  resolveHybridWarmupText,
  resolveMemoryPolicyForMode,
  resolveModeWithHybridReadiness,
  saveHybridBetaPreference,
} from '../src/views/qa/qa-mode-model.js'

test('问答模式只暴露智能推荐和后端真实支持的模式', () => {
  assert.deepEqual(BACKEND_QA_MODES, ['basic', 'local', 'global', 'drift', 'hybrid_v0'])
  assert.equal(SMART_QA_MODE, 'smart')
  assert.ok(QA_MODE_OPTIONS.some((option) => option.value === SMART_QA_MODE))
  assert.ok(QA_MODE_OPTIONS.some((option) => option.value === 'hybrid_v0'))
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'full'), false)
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'hybrid'), false)
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'auto'), false)
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value.includes('memory')), false)
})

test('智能推荐将事实和定义类问题路由到 basic', () => {
  const result = resolveQaMode('什么是信号量？', SMART_QA_MODE)

  assert.equal(result.mode, 'basic')
  assert.match(result.reason, /快速/)
})

test('智能推荐将章节定位和资料依据类问题路由到 local', () => {
  const result = resolveQaMode('请根据第 3 章解释进程调度算法', SMART_QA_MODE)

  assert.equal(result.mode, 'local')
  assert.match(result.reason, /课程资料|章节/)
})

test('智能推荐将整体综述类问题路由到 global', () => {
  const result = resolveQaMode('请综述这门课的知识体系和主题脉络', SMART_QA_MODE)

  assert.equal(result.mode, 'global')
  assert.match(result.reason, /整体/)
})

test('智能推荐将探索关联类问题路由到 drift', () => {
  const result = resolveQaMode('进程同步和数据库事务之间有什么关联，可以扩展说明吗？', SMART_QA_MODE)

  assert.equal(result.mode, 'drift')
  assert.match(result.reason, /关联|探索/)
})

test('手动选择模式时直接使用该后端模式', () => {
  const result = resolveQaMode('请解释死锁', 'hybrid_v0')

  assert.equal(result.mode, 'hybrid_v0')
  assert.equal(result.fromSmart, false)
})

test('智能推荐默认不自动路由到 hybrid_v0', () => {
  const result = resolveQaMode('请综合比较死锁和资源分配图的关系，并给出课程证据', SMART_QA_MODE)

  assert.notEqual(result.mode, 'hybrid_v0')
})

test('Beta 开启后智能推荐可受控路由到 hybrid_v0', () => {
  const result = resolveQaMode(
    '请综合比较死锁和资源分配图的关系，并给出课程证据',
    SMART_QA_MODE,
    { allowHybridBeta: true, hasConversationContext: true },
  )

  assert.equal(result.mode, 'hybrid_v0')
  assert.equal(result.fromSmart, true)
  assert.match(result.reason, /Beta|混合检索/)
})

test('智能推荐混合检索 Beta 偏好可持久化到本地存储', () => {
  const storage = new Map()
  const fakeStorage = {
    getItem(key) {
      return storage.has(key) ? storage.get(key) : null
    },
    setItem(key, value) {
      storage.set(key, String(value))
    },
  }

  assert.equal(loadHybridBetaPreference(fakeStorage), false)
  assert.equal(saveHybridBetaPreference(true, fakeStorage), true)
  assert.equal(loadHybridBetaPreference(fakeStorage), true)
  assert.equal(saveHybridBetaPreference(false, fakeStorage), true)
  assert.equal(loadHybridBetaPreference(fakeStorage), false)
})

test('服务端智能推荐结果优先于本地智能推荐兜底', () => {
  const local = resolveQaMode('进程同步和数据库事务之间有什么关联？', SMART_QA_MODE)
  const result = resolveQaModeRecommendation(local, {
    recommendedMode: 'hybrid_v0',
    reasonText: '服务端检测到证据融合需求',
    confidence: 0.82,
    confidenceBand: 'high_confidence',
    manualSwitchSuggested: false,
    reviewPriority: 'normal',
    reasons: ['evidence_relation_intent'],
  })

  assert.equal(result.mode, 'hybrid_v0')
  assert.equal(result.fromSmart, true)
  assert.equal(result.fromServer, true)
  assert.equal(result.confidenceBand, 'high_confidence')
  assert.equal(result.manualSwitchSuggested, false)
  assert.equal(result.reviewPriority, 'normal')
  assert.match(result.reason, /服务端/)
})

test('低置信度服务端推荐会提示手动切换并保留收集标记', () => {
  const local = resolveQaMode('请帮我复习一下。', SMART_QA_MODE)
  const result = resolveQaModeRecommendation(local, {
    recommendedMode: 'basic',
    fallbackMode: 'local',
    reasonText: '推荐不够确定，可手动切换模式',
    confidence: 0.59,
    confidenceBand: 'low_confidence',
    manualSwitchSuggested: true,
    reviewPriority: 'low_confidence',
    reasons: ['default_basic'],
  })

  assert.equal(result.mode, 'basic')
  assert.equal(result.confidenceBand, 'low_confidence')
  assert.equal(result.manualSwitchSuggested, true)
  assert.equal(result.reviewPriority, 'low_confidence')
})

test('智能推荐命中 hybrid 但 warmup 未 ready 时降级到 fallback', () => {
  const serverResolution = resolveQaModeRecommendation(resolveQaMode('什么是死锁？', SMART_QA_MODE), {
    recommendedMode: 'hybrid_v0',
    fallbackMode: 'local',
    reasonText: '服务端检测到证据融合需求',
    confidence: 0.71,
    confidenceBand: 'medium_confidence',
    manualSwitchSuggested: false,
    reasons: ['evidence_relation_intent'],
  })

  const result = resolveModeWithHybridReadiness(serverResolution, {
    selectedMode: SMART_QA_MODE,
    warmupStatus: 'not_ready',
  })

  assert.equal(result.mode, 'local')
  assert.equal(result.originalRecommendedMode, 'hybrid_v0')
  assert.equal(result.reviewPriority, 'hybrid_not_ready')
  assert.equal(result.manualSwitchSuggested, true)
  assert.match(result.reason, /准备中|降级/)
})

test('手动 hybrid 在 warmup 未 ready 时只提示不静默改写模式', () => {
  const manualResolution = resolveQaMode('请综合比较死锁和饥饿，并给出课程证据', 'hybrid_v0')
  const result = resolveModeWithHybridReadiness(manualResolution, {
    selectedMode: 'hybrid_v0',
    warmupStatus: 'not_ready',
  })

  assert.equal(result.mode, 'hybrid_v0')
  assert.equal(result.reviewPriority, 'hybrid_not_ready')
  assert.equal(result.manualSwitchSuggested, true)
})

test('混合检索 ready 且命中缓存时展示缓存就绪文案', () => {
  assert.equal(resolveHybridWarmupText('ready', true), '混合检索已就绪（缓存）')
  assert.equal(resolveHybridWarmupText('ready', false), '混合检索已就绪')
  assert.equal(resolveHybridWarmupText('not_ready', false), '混合检索准备中')
})

test('服务端返回未知模式时使用本地智能推荐兜底', () => {
  const local = resolveQaMode('什么是死锁？', SMART_QA_MODE)
  const result = resolveQaModeRecommendation(local, {
    recommendedMode: 'auto',
    reasonText: '非法模式',
  })

  assert.equal(result.mode, 'basic')
  assert.equal(result.fromServer, false)
})

test('学习记忆策略只跟随最终 Local 模式，不新增问答模式', () => {
  const smartLocal = resolveQaMode('请根据第 3 章解释进程调度算法', SMART_QA_MODE)

  assert.equal(smartLocal.mode, 'local')
  assert.equal(resolveMemoryPolicyForMode(smartLocal.mode, true), 'auto')
  assert.equal(resolveMemoryPolicyForMode('local', false), 'off')
  assert.equal(resolveMemoryPolicyForMode('global', true), 'off')
  assert.equal(resolveMemoryPolicyForMode('hybrid_v0', true), 'off')
})
