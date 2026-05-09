import test from 'node:test'
import assert from 'node:assert/strict'

import {
  STORAGE_KEY,
  EXPANDED_SIDEBAR_WIDTH,
  COLLAPSED_SIDEBAR_WIDTH,
  readCollapsed,
  writeCollapsed,
  isToggleKey,
} from './sidebar-collapse-model.js'

function createMockStorage() {
  const store = {}
  return {
    getItem: (key) => (key in store ? store[key] : null),
    setItem: (key, value) => {
      store[key] = String(value)
    },
    removeItem: (key) => {
      delete store[key]
    },
    _store: store,
  }
}

test('STORAGE_KEY 为 ckqa.sidebar.collapsed', () => {
  assert.equal(STORAGE_KEY, 'ckqa.sidebar.collapsed')
})

test('EXPANDED_SIDEBAR_WIDTH 为 240 / COLLAPSED_SIDEBAR_WIDTH 为 64', () => {
  assert.equal(EXPANDED_SIDEBAR_WIDTH, 240)
  assert.equal(COLLAPSED_SIDEBAR_WIDTH, 64)
})

test('readCollapsed 默认返回 false', () => {
  const storage = createMockStorage()
  assert.equal(readCollapsed(storage), false)
})

test('readCollapsed 读取到 "1" 时返回 true', () => {
  const storage = createMockStorage()
  storage.setItem(STORAGE_KEY, '1')
  assert.equal(readCollapsed(storage), true)
})

test('readCollapsed 读取到其他值返回 false', () => {
  const storage = createMockStorage()
  storage.setItem(STORAGE_KEY, 'yes')
  assert.equal(readCollapsed(storage), false)
})

test('writeCollapsed(true) 写入 "1"', () => {
  const storage = createMockStorage()
  writeCollapsed(true, storage)
  assert.equal(storage._store[STORAGE_KEY], '1')
})

test('writeCollapsed(false) 清除 key', () => {
  const storage = createMockStorage()
  storage.setItem(STORAGE_KEY, '1')
  writeCollapsed(false, storage)
  assert.equal(STORAGE_KEY in storage._store, false)
})

test('writeCollapsed 在存储抛错时静默', () => {
  const storage = {
    getItem: () => null,
    setItem: () => {
      throw new Error('quota')
    },
    removeItem: () => {
      throw new Error('quota')
    },
  }
  // 不应抛出
  writeCollapsed(true, storage)
  writeCollapsed(false, storage)
})

test('isToggleKey 识别 Ctrl+\\', () => {
  assert.equal(
    isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, isComposing: false }),
    true,
  )
})

test('isToggleKey 识别 Meta+\\（Mac）', () => {
  assert.equal(
    isToggleKey({ key: '\\', ctrlKey: false, metaKey: true, isComposing: false }),
    true,
  )
})

test('isToggleKey 没有 Ctrl/Meta 时返回 false', () => {
  assert.equal(
    isToggleKey({ key: '\\', ctrlKey: false, metaKey: false, isComposing: false }),
    false,
  )
})

test('isToggleKey 非 \\ 键返回 false', () => {
  assert.equal(
    isToggleKey({ key: 'k', ctrlKey: true, metaKey: false, isComposing: false }),
    false,
  )
})

test('isToggleKey 在 IME 合成态（isComposing=true）时返回 false', () => {
  assert.equal(
    isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, isComposing: true }),
    false,
  )
})

test('isToggleKey 在 keyCode=229（IME 中文候选词）时返回 false', () => {
  assert.equal(
    isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, keyCode: 229 }),
    false,
  )
})

test('isToggleKey 空 event 返回 false', () => {
  assert.equal(isToggleKey(null), false)
})
