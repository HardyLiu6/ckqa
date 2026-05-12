import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 创建网络状态处理器（纯逻辑，不依赖 DOM 或 Vue 生命周期）
 *
 * 用于测试和内部复用。返回事件处理函数和响应式状态。
 *
 * @param {object} [options]
 * @param {boolean} [options.initialOnline=true] - 初始在线状态
 * @param {number} [options.recoveryResetDelay=3000] - wasOffline 重置延迟（毫秒）
 * @returns {{ isOnline: import('vue').Ref<boolean>, wasOffline: import('vue').Ref<boolean>, handleOnline: Function, handleOffline: Function, cleanup: Function }}
 */
export function createNetworkStatusCore(options = {}) {
  const { initialOnline = true, recoveryResetDelay = 3000 } = options

  const isOnline = ref(initialOnline)
  const wasOffline = ref(false)

  let resetTimer = null

  function handleOnline() {
    // 只有从离线恢复时才标记 wasOffline
    if (!isOnline.value) {
      wasOffline.value = true

      // 短暂后重置 wasOffline，供 UI 展示恢复提示
      resetTimer = setTimeout(() => {
        wasOffline.value = false
        resetTimer = null
      }, recoveryResetDelay)
    }
    isOnline.value = true
  }

  function handleOffline() {
    isOnline.value = false
  }

  function cleanup() {
    if (resetTimer !== null) {
      clearTimeout(resetTimer)
      resetTimer = null
    }
  }

  return { isOnline, wasOffline, handleOnline, handleOffline, cleanup }
}

/**
 * 网络状态检测 composable
 *
 * 提供响应式的网络在线/离线状态，以及网络恢复标记。
 * 当网络从离线恢复为在线时，`wasOffline` 会短暂设为 true（用于触发恢复提示），
 * 然后在指定延迟后自动重置为 false。
 *
 * SSR 安全：在非浏览器环境下默认为在线状态且不注册事件监听。
 *
 * @param {object} [options]
 * @param {number} [options.recoveryResetDelay=3000] - wasOffline 重置延迟（毫秒）
 * @returns {{ isOnline: import('vue').Ref<boolean>, wasOffline: import('vue').Ref<boolean> }}
 */
export function useNetworkStatus(options = {}) {
  const { recoveryResetDelay = 3000 } = options

  const isBrowser = typeof window !== 'undefined'

  const { isOnline, wasOffline, handleOnline, handleOffline, cleanup } = createNetworkStatusCore({
    initialOnline: isBrowser ? navigator.onLine : true,
    recoveryResetDelay,
  })

  if (isBrowser) {
    onMounted(() => {
      window.addEventListener('online', handleOnline)
      window.addEventListener('offline', handleOffline)
    })

    onUnmounted(() => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      cleanup()
    })
  }

  return { isOnline, wasOffline }
}
