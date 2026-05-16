import axios from 'axios'

const viteEnv = import.meta.env ?? {}

export const API_BASE_URL = viteEnv.VITE_API_BASE_URL ?? '/api/v1'
export const API_TIMEOUT = Number(viteEnv.VITE_API_TIMEOUT ?? 15000)

let authSessionProvider = () => null

export function setAuthSessionProvider(provider) {
  authSessionProvider = typeof provider === 'function' ? provider : () => null
}

export function createHttpClient({ authStore: activeAuthStore = null, getAuthSession = null } = {}) {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: API_TIMEOUT,
  })

  client.interceptors.request.use((config) => {
    const headers = { ...(config.headers ?? {}) }
    const session = getAuthSession?.() ?? activeAuthStore?.state ?? authSessionProvider?.() ?? {}

    if (session.token) {
      headers.Authorization = `Bearer ${session.token}`
    }
    if (session.currentUser?.userCode) {
      headers['X-CKQA-User-Code'] = session.currentUser.userCode
    }

    return {
      ...config,
      headers,
    }
  })

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      const status = error.response?.status
      // axios 超时（ECONNABORTED + 'timeout of Xms exceeded'）的英文报错对终端用户不友好，
      // 在拦截器里统一转成中文。区分超时和其它网络错误。
      const isTimeout = error.code === 'ECONNABORTED'
        || (typeof error.message === 'string' && error.message.toLowerCase().includes('timeout'))

      // 后端非 2xx 响应（如 404 / 500）但 body 仍是 ApiResponse envelope 时，
      // 把 envelope 的 code / message / data 提到错误对象顶层，让上层能用
      // err.code / err.data 直接判别业务码（如 4105 / 4104）。
      // 这与 client.js unwrapApiResponse 的 200 + 业务非 200 抛错形态保持一致。
      const responseBody = error.response?.data
      const isApiEnvelope = responseBody
        && typeof responseBody === 'object'
        && Object.prototype.hasOwnProperty.call(responseBody, 'code')
        && Object.prototype.hasOwnProperty.call(responseBody, 'message')
        && Object.prototype.hasOwnProperty.call(responseBody, 'data')

      const businessCode = isApiEnvelope ? responseBody.code : undefined
      const businessMessage = isApiEnvelope ? responseBody.message : undefined
      const businessData = isApiEnvelope ? responseBody.data : undefined

      const message = isTimeout
        ? '请求超时，请稍后重试'
        : (businessMessage ?? error.message ?? '请求失败')

      return Promise.reject({
        status,
        code: businessCode,
        message,
        timeout: isTimeout,
        data: isApiEnvelope ? businessData : responseBody,
        envelope: isApiEnvelope ? responseBody : null,
        raw: error,
      })
    },
  )

  return client
}

export const http = createHttpClient()
