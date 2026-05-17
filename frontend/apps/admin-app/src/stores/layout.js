import { defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { getAdminPinia } from './pinia.js'

const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

// 断点阈值
const BREAKPOINT_DESKTOP = 1200
const BREAKPOINT_TABLET = 768

const STORAGE_KEY = 'ckqa.admin.sidebar-collapsed'

/**
 * 根据视口宽度确定自然 sidebarMode（不考虑用户手动折叠覆盖）。
 * @returns {'full' | 'icon' | 'hidden'}
 */
export function resolveSidebarMode(width) {
  if (width >= BREAKPOINT_DESKTOP) return 'full'
  if (width >= BREAKPOINT_TABLET) return 'icon'
  return 'hidden'
}

function readPersistedCollapsed() {
  if (!isBrowser) return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === 'true') return true
    if (raw === 'false') return false
    return null
  } catch {
    return null
  }
}

function persistCollapsed(value) {
  if (!isBrowser) return
  try {
    if (value === null) {
      window.localStorage.removeItem(STORAGE_KEY)
    } else {
      window.localStorage.setItem(STORAGE_KEY, String(value))
    }
  } catch {
    // ignore
  }
}

/**
 * 综合视口 + 用户手动折叠决定生效的 sidebarMode。
 *
 * @param {'full'|'icon'|'hidden'} natural 视口决定的自然态
 * @param {boolean|null} manual 用户手动折叠覆盖（true=折叠/icon, false=展开/full, null=不覆盖）
 */
export function resolveEffectiveSidebarMode(natural, manual) {
  if (natural === 'hidden') return 'hidden'  // 移动端 drawer 接管，不允许桌面折叠覆盖
  if (manual === true) return 'icon'
  if (manual === false) return 'full'
  return natural
}

export const useLayoutStore = defineStore('layout', () => {
  const state = reactive({
    /**
     * 当前生效的 sidebarMode（综合视口 + 手动折叠）。
     * 业务代码读这个字段就行；syncViewport / toggleCollapse 内部维护。
     */
    sidebarMode: 'full',
    /**
     * 视口决定的自然态（仅作为 computed 输入用，不直接给 UI 读）。
     */
    naturalSidebarMode: 'full',
    /**
     * 用户手动折叠覆盖：
     * - true  → 桌面态强制 icon
     * - false → 桌面态强制 full
     * - null  → 不覆盖，跟随自然态
     * 仅在 hidden 视口外生效。
     */
    manualCollapsed: null,
    isMobileMenuOpen: false,
  })

  let resizeCleanup = null

  function recompute() {
    state.sidebarMode = resolveEffectiveSidebarMode(
      state.naturalSidebarMode,
      state.manualCollapsed,
    )
  }

  function syncViewport(width) {
    state.naturalSidebarMode = resolveSidebarMode(width)
    recompute()
    // 非移动端时自动关闭移动菜单
    if (state.sidebarMode !== 'hidden') {
      state.isMobileMenuOpen = false
    }
  }

  function toggleMobileMenu() {
    state.isMobileMenuOpen = !state.isMobileMenuOpen
  }

  /**
   * 用户手动折叠 / 展开（仅桌面 / 平板态生效）。
   * full → icon → full ↔ ...；hidden 模式下点击不生效（drawer 接管）。
   */
  function toggleCollapse() {
    if (state.sidebarMode === 'hidden') return
    const next = state.sidebarMode === 'full' ? true : false
    state.manualCollapsed = next
    persistCollapsed(next)
    recompute()
  }

  /**
   * 清空手动覆盖，回到响应式自然行为。
   */
  function resetCollapse() {
    state.manualCollapsed = null
    persistCollapsed(null)
    recompute()
  }

  function initLayout() {
    if (!isBrowser) return
    state.manualCollapsed = readPersistedCollapsed()
    syncViewport(window.innerWidth)
    const handleResize = () => syncViewport(window.innerWidth)
    window.addEventListener('resize', handleResize)
    resizeCleanup = () => window.removeEventListener('resize', handleResize)
  }

  function destroy() {
    if (resizeCleanup) {
      resizeCleanup()
      resizeCleanup = null
    }
  }

  return {
    state: readonly(state),
    syncViewport,
    toggleMobileMenu,
    toggleCollapse,
    resetCollapse,
    initLayout,
    destroy,
  }
})

export function createLayoutStore(pinia) {
  return useLayoutStore(pinia ?? getAdminPinia())
}

export const layoutStore = useLayoutStore(getAdminPinia())
