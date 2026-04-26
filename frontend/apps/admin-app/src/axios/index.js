import axios from 'axios'

import { authStore } from '../stores/auth.js'

const viteEnv = import.meta.env ?? {}

export const API_BASE_URL = viteEnv.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080/api/v1'
export const API_TIMEOUT = Number(viteEnv.VITE_API_TIMEOUT ?? 15000)

export function createHttpClient({ authStore: activeAuthStore = authStore } = {}) {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: API_TIMEOUT,
  })

  client.interceptors.request.use((config) => {
    const headers = { ...(config.headers ?? {}) }

    if (activeAuthStore.state.token) {
      headers.Authorization = `Bearer ${activeAuthStore.state.token}`
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
      const message = error.response?.data?.message ?? error.message ?? '请求失败'

      return Promise.reject({
        status,
        message,
        raw: error,
      })
    },
  )

  return client
}

export const http = createHttpClient()
