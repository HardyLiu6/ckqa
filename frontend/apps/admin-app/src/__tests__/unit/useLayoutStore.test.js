import test from 'node:test'
import assert from 'node:assert/strict'

import { createPinia } from 'pinia'

import {
  createLayoutStore,
  resolveSidebarMode,
  useLayoutStore,
} from '../../stores/layout.js'

// --- resolveSidebarMode 纯函数测试 ---

test('resolveSidebarMode 宽度 >= 1200 返回 full', () => {
  assert.equal(resolveSidebarMode(1200), 'full')
  assert.equal(resolveSidebarMode(1920), 'full')
  assert.equal(resolveSidebarMode(2560), 'full')
})

test('resolveSidebarMode 宽度 768-1199 返回 icon', () => {
  assert.equal(resolveSidebarMode(768), 'icon')
  assert.equal(resolveSidebarMode(1024), 'icon')
  assert.equal(resolveSidebarMode(1199), 'icon')
})

test('resolveSidebarMode 宽度 < 768 返回 hidden', () => {
  assert.equal(resolveSidebarMode(767), 'hidden')
  assert.equal(resolveSidebarMode(375), 'hidden')
  assert.equal(resolveSidebarMode(320), 'hidden')
  assert.equal(resolveSidebarMode(0), 'hidden')
})

// --- useLayoutStore 实例测试 ---

test('useLayoutStore 初始状态为 sidebarMode=full, isMobileMenuOpen=false', () => {
  const store = createLayoutStore(createPinia())
  assert.equal(store.state.sidebarMode, 'full')
  assert.equal(store.state.isMobileMenuOpen, false)
})

test('syncViewport 根据宽度正确切换 sidebarMode', () => {
  const store = createLayoutStore(createPinia())

  store.syncViewport(1920)
  assert.equal(store.state.sidebarMode, 'full')

  store.syncViewport(1024)
  assert.equal(store.state.sidebarMode, 'icon')

  store.syncViewport(375)
  assert.equal(store.state.sidebarMode, 'hidden')
})

test('syncViewport 在非移动端时自动关闭移动菜单', () => {
  const store = createLayoutStore(createPinia())

  // 先切换到移动端并打开菜单
  store.syncViewport(375)
  store.toggleMobileMenu()
  assert.equal(store.state.isMobileMenuOpen, true)

  // 切换到桌面端，菜单应自动关闭
  store.syncViewport(1920)
  assert.equal(store.state.isMobileMenuOpen, false)
})

test('syncViewport 在 icon 模式下也关闭移动菜单', () => {
  const store = createLayoutStore(createPinia())

  store.syncViewport(375)
  store.toggleMobileMenu()
  assert.equal(store.state.isMobileMenuOpen, true)

  store.syncViewport(1024)
  assert.equal(store.state.isMobileMenuOpen, false)
})

test('toggleMobileMenu 切换 isMobileMenuOpen 状态', () => {
  const store = createLayoutStore(createPinia())

  assert.equal(store.state.isMobileMenuOpen, false)

  store.toggleMobileMenu()
  assert.equal(store.state.isMobileMenuOpen, true)

  store.toggleMobileMenu()
  assert.equal(store.state.isMobileMenuOpen, false)
})

test('state 是只读的，不能直接修改', () => {
  const store = createLayoutStore(createPinia())

  // readonly 对象赋值在严格模式下会静默失败
  store.state.sidebarMode = 'hidden'
  assert.equal(store.state.sidebarMode, 'full')
})

test('createLayoutStore 使用独立 pinia 实例创建隔离的 store', () => {
  const store1 = createLayoutStore(createPinia())
  const store2 = createLayoutStore(createPinia())

  store1.syncViewport(375)
  assert.equal(store1.state.sidebarMode, 'hidden')
  assert.equal(store2.state.sidebarMode, 'full')
})

test('useLayoutStore 可通过同一 pinia 实例共享状态', () => {
  const pinia = createPinia()
  const store1 = useLayoutStore(pinia)
  const store2 = useLayoutStore(pinia)

  store1.syncViewport(375)
  assert.equal(store2.state.sidebarMode, 'hidden')
})

test('initLayout 在非浏览器环境下安全执行不抛错', () => {
  const store = createLayoutStore(createPinia())
  // 在 Node.js 环境下调用 initLayout 不应抛出异常
  // 因为 isBrowser 检查会跳过 window 操作
  assert.doesNotThrow(() => store.initLayout())
})

test('destroy 在未初始化时安全调用不抛错', () => {
  const store = createLayoutStore(createPinia())
  assert.doesNotThrow(() => store.destroy())
})

test('断点边界值精确匹配', () => {
  // 精确测试边界值
  assert.equal(resolveSidebarMode(1200), 'full')
  assert.equal(resolveSidebarMode(1199), 'icon')
  assert.equal(resolveSidebarMode(768), 'icon')
  assert.equal(resolveSidebarMode(767), 'hidden')
})
