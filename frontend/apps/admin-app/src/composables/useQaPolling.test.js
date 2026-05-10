import test from 'node:test'
import assert from 'node:assert/strict'
import fc from 'fast-check'

import {
  isQaTerminalState,
  resolveQaPollingInterval,
  resolveQaStaleTimeout,
} from './useQaPolling.js'

test('QA 轮询模型优先使用后端提示并按模式提供默认值', () => {
  assert.equal(resolveQaPollingInterval({ recommendedPollingIntervalSeconds: 4 }, 'global').intervalMs, 4000)
  assert.equal(resolveQaStaleTimeout({ staleTimeoutSeconds: 45 }, 'drift').timeoutMs, 45000)
  assert.equal(resolveQaPollingInterval({ mode: 'drift' }, 'basic').intervalMs, 30000)
  assert.equal(resolveQaStaleTimeout({}, 'global').timeoutMs, 1800000)
  assert.equal(resolveQaPollingInterval({}, 'basic').intervalMs, 10000)
  assert.equal(resolveQaStaleTimeout({}, undefined).timeoutMs, 300000)
})

test('QA 终态识别覆盖成功、失败和超时状态', () => {
  assert.equal(isQaTerminalState('success'), true)
  assert.equal(isQaTerminalState('completed'), true)
  assert.equal(isQaTerminalState('failed'), true)
  assert.equal(isQaTerminalState('timeout'), true)
  assert.equal(isQaTerminalState('running'), false)
  assert.equal(isQaTerminalState('queued'), false)
})

// ---------------------------------------------------------------------------
// P4 属性测试：useQaPolling 迁移期等价性
// ---------------------------------------------------------------------------
// 背景：任务 1.1 已用 smartRelocate 把 `src/views/pages/qa-polling.js` 迁移到
// `src/composables/useQaPolling.js`，旧文件在本 worktree 下已物理删除。
// 因此 tasks.md 中"新/旧实现一致"这一步在本仓库没有旧实现可比；P4 属性退化为
// 对迁移后新实现在随机合法输入下必须满足的三条不变量——这也是迁移等价性的
// 最小充分条件。
//
// 预期常量必须与 `useQaPolling.js` 内部的 DEFAULT_LIMITS_BY_MODE 保持同步；
// 若后续修改源码常量，此处也需同步，否则 P4 会红灯，正是提醒作用。
//
// **Validates: Requirements P4, CC-2, design §13.3**

const EXPECTED_DEFAULT_LIMITS_BY_MODE = {
  local: { intervalMs: 10000, timeoutMs: 300000 },
  basic: { intervalMs: 10000, timeoutMs: 300000 },
  global: { intervalMs: 30000, timeoutMs: 1800000 },
  drift: { intervalMs: 30000, timeoutMs: 1800000 },
}

const KNOWN_MODES = Object.keys(EXPECTED_DEFAULT_LIMITS_BY_MODE)

// 生成器：mode 覆盖「已知模式 + 任意字符串（模拟未知模式兜底）」
const modeArb = fc.oneof(fc.constantFrom(...KNOWN_MODES), fc.string({ maxLength: 16 }))

// 生成器：seconds 覆盖「正整数 / 0 / 负数 / undefined / null / NaN」以覆盖
// "正数走 seconds * 1000 分支"与"非正数退回 mode 默认值"两条路径
const secondsArb = fc.oneof(
  fc.integer({ min: 0, max: 3600 }),
  fc.constant(undefined),
  fc.constant(null),
  fc.constant(-1),
  fc.constant(Number.NaN),
)

test('P4 属性：resolveQaPollingInterval / resolveQaStaleTimeout 对随机合法输入满足等价性约束', () => {
  fc.assert(
    fc.property(
      fc.record({
        status: fc.string({ maxLength: 16 }),
        elapsedMs: fc.integer({ min: 0, max: 1_000_000 }),
        mode: modeArb,
        kind: fc.string({ maxLength: 16 }),
        recommendedPollingIntervalSeconds: secondsArb,
        staleTimeoutSeconds: secondsArb,
      }),
      fc.option(modeArb, { nil: undefined }),
      (task, requestMode) => {
        const pollingResult = resolveQaPollingInterval(task, requestMode)
        const staleResult = resolveQaStaleTimeout(task, requestMode)

        // ① 结构不变量：返回 { intervalMs } / { timeoutMs }，且均为正整数
        assert.ok(
          pollingResult && typeof pollingResult === 'object',
          'resolveQaPollingInterval 必须返回对象',
        )
        assert.ok(
          staleResult && typeof staleResult === 'object',
          'resolveQaStaleTimeout 必须返回对象',
        )
        assert.equal(Number.isInteger(pollingResult.intervalMs), true)
        assert.ok(pollingResult.intervalMs > 0)
        assert.equal(Number.isInteger(staleResult.timeoutMs), true)
        assert.ok(staleResult.timeoutMs > 0)

        // ② mode 默认值不变量：当 seconds 非正数时，结果等于 DEFAULT_LIMITS_BY_MODE
        //    的对应项；未知 mode 按 `local` 兜底
        const effectiveMode = String(task.mode ?? requestMode ?? 'local').toLowerCase()
        const defaults = Object.hasOwn(EXPECTED_DEFAULT_LIMITS_BY_MODE, effectiveMode)
          ? EXPECTED_DEFAULT_LIMITS_BY_MODE[effectiveMode]
          : EXPECTED_DEFAULT_LIMITS_BY_MODE.local

        // ③ seconds 覆写不变量：seconds 为正数时结果 = seconds * 1000，
        //    否则退回 mode 默认值
        const recSec = Number(task.recommendedPollingIntervalSeconds)
        const expectedInterval =
          Number.isFinite(recSec) && recSec > 0 ? recSec * 1000 : defaults.intervalMs
        assert.equal(pollingResult.intervalMs, expectedInterval)

        const staleSec = Number(task.staleTimeoutSeconds)
        const expectedTimeout =
          Number.isFinite(staleSec) && staleSec > 0 ? staleSec * 1000 : defaults.timeoutMs
        assert.equal(staleResult.timeoutMs, expectedTimeout)
      },
    ),
    { numRuns: 50 },
  )
})
