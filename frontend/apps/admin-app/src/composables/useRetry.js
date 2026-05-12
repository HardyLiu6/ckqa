import { ref } from 'vue'

/**
 * 重试 composable — 封装异步请求的重试逻辑与升级提示状态。
 *
 * @param {Function} fetchFn - 异步请求函数，返回 Promise
 * @param {Object} options - 配置选项
 * @param {number} options.maxRetries - 最大重试次数阈值，超过后触发升级提示（默认 3）
 * @returns {{ retryCount, isEscalated, loading, error, data, execute, retry, reset }}
 */
export function useRetry(fetchFn, options = {}) {
  const { maxRetries = 3 } = options

  // 当前连续失败次数
  const retryCount = ref(0)
  // 是否已达到升级阈值
  const isEscalated = ref(false)
  // 是否正在请求中
  const loading = ref(false)
  // 最近一次错误
  const error = ref(null)
  // 成功时的数据
  const data = ref(null)

  /**
   * 首次执行请求。成功时重置 retryCount 和 isEscalated。
   */
  async function execute() {
    loading.value = true
    error.value = null

    try {
      const result = await fetchFn()
      data.value = result
      // 成功时重置重试计数和升级状态
      retryCount.value = 0
      isEscalated.value = false
      return result
    } catch (err) {
      error.value = err
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * 重试请求。失败时递增 retryCount，当 retryCount >= maxRetries 时设置 isEscalated = true。
   * 无论 isEscalated 状态如何，retry 方法始终可调用。
   */
  async function retry() {
    loading.value = true
    error.value = null

    try {
      const result = await fetchFn()
      data.value = result
      // 成功时重置重试计数和升级状态
      retryCount.value = 0
      isEscalated.value = false
      return result
    } catch (err) {
      error.value = err
      retryCount.value += 1
      if (retryCount.value >= maxRetries) {
        isEscalated.value = true
      }
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置所有状态到初始值。
   */
  function reset() {
    retryCount.value = 0
    isEscalated.value = false
    loading.value = false
    error.value = null
    data.value = null
  }

  return {
    retryCount,
    isEscalated,
    loading,
    error,
    data,
    execute,
    retry,
    reset,
  }
}
