import test from 'node:test'
import assert from 'node:assert/strict'
import { createNetworkStatusCore, useNetworkStatus } from '../../composables/useNetworkStatus.js'

test('useNetworkStatus 模块导出命名函数', () => {
  assert.equal(typeof useNetworkStatus, 'function')
  assert.equal(typeof createNetworkStatusCore, 'function')
})

test('createNetworkStatusCore 默认初始状态为在线', () => {
  const { isOnline, wasOffline } = createNetworkStatusCore()

  assert.equal(isOnline.value, true)
  assert.equal(wasOffline.value, false)
})

test('createNetworkStatusCore 支持自定义初始离线状态', () => {
  const { isOnline, wasOffline } = createNetworkStatusCore({ initialOnline: false })

  assert.equal(isOnline.value, false)
  assert.equal(wasOffline.value, false)
})

test('handleOffline 将 isOnline 设为 false', () => {
  const { isOnline, handleOffline } = createNetworkStatusCore({ initialOnline: true })

  assert.equal(isOnline.value, true)

  handleOffline()

  assert.equal(isOnline.value, false)
})

test('handleOnline 从离线恢复时将 isOnline 设为 true 并标记 wasOffline', () => {
  const originalSetTimeout = globalThis.setTimeout
  let timerCallback = null
  let timerDelay = null
  globalThis.setTimeout = (cb, delay) => {
    timerCallback = cb
    timerDelay = delay
    return 42
  }

  try {
    const { isOnline, wasOffline, handleOffline, handleOnline } = createNetworkStatusCore({
      initialOnline: true,
      recoveryResetDelay: 3000,
    })

    // 先断网
    handleOffline()
    assert.equal(isOnline.value, false)

    // 恢复网络
    handleOnline()
    assert.equal(isOnline.value, true)
    assert.equal(wasOffline.value, true)
    assert.equal(timerDelay, 3000)

    // 模拟定时器触发后 wasOffline 重置
    timerCallback()
    assert.equal(wasOffline.value, false)
  } finally {
    globalThis.setTimeout = originalSetTimeout
  }
})

test('handleOnline 在已经在线时不触发 wasOffline', () => {
  const originalSetTimeout = globalThis.setTimeout
  let timeoutCalled = false
  globalThis.setTimeout = () => {
    timeoutCalled = true
    return 1
  }

  try {
    const { isOnline, wasOffline, handleOnline } = createNetworkStatusCore({ initialOnline: true })

    assert.equal(isOnline.value, true)

    // 已在线时触发 online 事件
    handleOnline()

    assert.equal(isOnline.value, true)
    assert.equal(wasOffline.value, false)
    assert.equal(timeoutCalled, false)
  } finally {
    globalThis.setTimeout = originalSetTimeout
  }
})

test('recoveryResetDelay 参数控制 wasOffline 重置延迟', () => {
  const originalSetTimeout = globalThis.setTimeout
  let timerDelay = null
  globalThis.setTimeout = (cb, delay) => {
    timerDelay = delay
    return 1
  }

  try {
    const { handleOffline, handleOnline } = createNetworkStatusCore({
      initialOnline: true,
      recoveryResetDelay: 5000,
    })

    handleOffline()
    handleOnline()

    assert.equal(timerDelay, 5000)
  } finally {
    globalThis.setTimeout = originalSetTimeout
  }
})

test('cleanup 清除待执行的重置定时器', () => {
  const originalSetTimeout = globalThis.setTimeout
  const originalClearTimeout = globalThis.clearTimeout
  let clearedId = null
  globalThis.setTimeout = (cb, delay) => 99
  globalThis.clearTimeout = (id) => { clearedId = id }

  try {
    const { handleOffline, handleOnline, wasOffline, cleanup } = createNetworkStatusCore({
      initialOnline: true,
      recoveryResetDelay: 3000,
    })

    handleOffline()
    handleOnline()
    assert.equal(wasOffline.value, true)

    // cleanup 应清除定时器
    cleanup()
    assert.equal(clearedId, 99)
  } finally {
    globalThis.setTimeout = originalSetTimeout
    globalThis.clearTimeout = originalClearTimeout
  }
})

test('多次离线/在线切换只保留最新的重置定时器', () => {
  const originalSetTimeout = globalThis.setTimeout
  const originalClearTimeout = globalThis.clearTimeout
  const timerIds = []
  let nextId = 1
  const clearedIds = []

  globalThis.setTimeout = (cb, delay) => {
    const id = nextId++
    timerIds.push(id)
    return id
  }
  globalThis.clearTimeout = (id) => { clearedIds.push(id) }

  try {
    const { handleOffline, handleOnline, wasOffline } = createNetworkStatusCore({
      initialOnline: true,
      recoveryResetDelay: 3000,
    })

    // 第一次离线→在线
    handleOffline()
    handleOnline()
    assert.equal(wasOffline.value, true)
    assert.equal(timerIds.length, 1)

    // 再次离线→在线（模拟网络不稳定）
    handleOffline()
    handleOnline()
    assert.equal(wasOffline.value, true)
    assert.equal(timerIds.length, 2)
  } finally {
    globalThis.setTimeout = originalSetTimeout
    globalThis.clearTimeout = originalClearTimeout
  }
})
