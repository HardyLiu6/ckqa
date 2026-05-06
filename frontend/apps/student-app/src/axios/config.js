export const DEFAULT_API_BASE_URL = '/api/v1'
export const DEFAULT_API_TIMEOUT = 10000
export const DEFAULT_ERROR_MESSAGE = '请求失败，请稍后重试'
export const DEFAULT_TIMEOUT_ERROR_MESSAGE = '请求超时，请稍后重试'

export function normalizeBaseURL(baseURL) {
  if (typeof baseURL !== 'string') {
    return DEFAULT_API_BASE_URL
  }

  const normalized = baseURL.trim()
  if (!normalized) {
    return DEFAULT_API_BASE_URL
  }

  if (normalized === '/') {
    return normalized
  }

  return normalized.replace(/\/+$/, '')
}

export function parseTimeout(timeoutValue) {
  const parsed = Number.parseInt(timeoutValue, 10)
  if (Number.isFinite(parsed) && parsed > 0) {
    return parsed
  }

  return DEFAULT_API_TIMEOUT
}

export function createRequestRuntime(env = {}) {
  return {
    baseURL: normalizeBaseURL(env.VITE_API_BASE_URL),
    timeout: parseTimeout(env.VITE_API_TIMEOUT),
  }
}

export function resolveResponsePayload(payload) {
  if (
    payload &&
    typeof payload === 'object' &&
    'data' in payload &&
    ('code' in payload || 'success' in payload || 'message' in payload)
  ) {
    return payload.data
  }

  return payload
}

export function buildErrorMessage(error) {
  const responseData = error?.response?.data
  if (typeof responseData === 'string' && responseData.trim()) {
    return responseData.trim()
  }

  const serverMessage =
    responseData?.message || responseData?.detail || responseData?.error?.message
  if (typeof serverMessage === 'string' && serverMessage.trim()) {
    return serverMessage.trim()
  }

  const rawMessage = typeof error?.message === 'string' ? error.message.trim() : ''
  if (error?.code === 'ECONNABORTED' || rawMessage.toLowerCase().includes('timeout')) {
    return DEFAULT_TIMEOUT_ERROR_MESSAGE
  }

  if (rawMessage) {
    return rawMessage
  }

  return DEFAULT_ERROR_MESSAGE
}
