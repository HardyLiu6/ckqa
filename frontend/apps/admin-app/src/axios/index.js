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
      const message = error.message ?? '请求失败'

      return Promise.reject({
        status,
        message,
        data: error.response?.data,
        raw: error,
      })
    },
  )

  return client
}

export const http = createHttpClient()
