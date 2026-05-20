import axios from 'axios'

import {
  buildErrorMessage,
  createRequestRuntime,
  resolveResponsePayload,
} from './config.js'

export const apiRuntime = createRequestRuntime(import.meta.env)

const request = axios.create({
  baseURL: apiRuntime.baseURL,
  timeout: apiRuntime.timeout,
  headers: {
    Accept: 'application/json',
  },
})

let authSessionProvider = () => null

export function setAuthSessionProvider(provider) {
  authSessionProvider = typeof provider === 'function' ? provider : () => null
}

export function resolveAuthHeaders() {
  const session = authSessionProvider?.() ?? {}
  const headers = {}

  if (session.token) {
    headers.Authorization = `Bearer ${session.token}`
  }
  if (session.user?.userCode) {
    headers['X-CKQA-User-Code'] = session.user.userCode
  }

  return headers
}

request.interceptors.request.use((config) => {
  const headers = { ...(config.headers ?? {}) }

  return {
    ...config,
    headers: {
      ...headers,
      ...resolveAuthHeaders(),
    },
  }
})

request.interceptors.response.use(
  (response) => resolveResponsePayload(response.data),
  (error) => {
    const normalizedError = new Error(buildErrorMessage(error))
    normalizedError.code = error?.code
    normalizedError.status = error?.response?.status
    normalizedError.response = error?.response
    normalizedError.cause = error
    return Promise.reject(normalizedError)
  },
)

export const get = (url, config = {}) => request.get(url, config)
export const post = (url, data, config = {}) => request.post(url, data, config)
export const put = (url, data, config = {}) => request.put(url, data, config)
export const patch = (url, data, config = {}) => request.patch(url, data, config)
export const del = (url, config = {}) => request.delete(url, config)

export { request }
export default request
