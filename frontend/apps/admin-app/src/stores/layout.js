import { defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { getAdminPinia } from './pinia.js'

const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

// 断点阈值
const BREAKPOINT_DESKTOP = 1200
const BREAKPOINT_TABLET = 768

/**
 * 根据视口宽度确定 sidebarMode
 * @param {number} width - 视口宽度（px）
 * @returns {'full' | 'icon' | 'hidden'}
 */
export function resolveSidebarMode(width) {
  if (width >= BREAKPOINT_DESKTOP) return 'full'
  if (width >= BREAKPOINT_TABLET) return 'icon'
  return 'hidden'
}

export const useLayoutStore = defineStore('layout', () => {
  const state = reactive({
    sidebarMode: 'full', // 'full' | 'icon' | 'hidden'
    isMobileMenuOpen: false,
  })

  let resizeCleanup = null

  /**
   * 根据视口宽度同步 sidebarMode
   * @param {number} width - 视口宽度（px）
   */
  function syncViewport(width) {
    state.sidebarMode = resolveSidebarMode(width)
    // 非移动端时自动关闭移动菜单
    if (state.sidebarMode !== 'hidden') {
      state.isMobileMenuOpen = false
    }
  }

  /**
   * 切换移动端菜单开关
   */
  function toggleMobileMenu() {
    state.isMobileMenuOpen = !state.isMobileMenuOpen
  }

  /**
   * 初始化布局，监听 resize 事件响应视口变化
   * SSR 安全：仅在浏览器环境下执行
   */
  function initLayout() {
    if (!isBrowser) return

    // 初始同步当前视口
    syncViewport(window.innerWidth)

    // 使用 ResizeObserver 或 resize 事件监听视口变化
    const handleResize = () => {
      syncViewport(window.innerWidth)
    }

    window.addEventListener('resize', handleResize)

    // 保存清理函数
    resizeCleanup = () => {
      window.removeEventListener('resize', handleResize)
    }
  }

  /**
   * 销毁监听器（用于测试或组件卸载）
   */
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
    initLayout,
    destroy,
  }
})

export function createLayoutStore(pinia) {
  return useLayoutStore(pinia ?? getAdminPinia())
}

export const layoutStore = useLayoutStore(getAdminPinia())
