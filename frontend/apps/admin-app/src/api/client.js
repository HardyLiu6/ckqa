export function unwrapApiResponse(response) {
  const body = response?.data
  const isEnvelope = body && Object.prototype.hasOwnProperty.call(body, 'code')
    && Object.prototype.hasOwnProperty.call(body, 'message')
    && Object.prototype.hasOwnProperty.call(body, 'data')

  if (!isEnvelope) {
    throw {
      message: '后端响应格式不符合 CKQA ApiResponse 契约',
      nonStandard: true,
      status: response?.status,
      raw: body ?? response,
    }
  }

  if (body.code === 200) {
    return body.data
  }

  throw {
    message: body.message || '业务请求失败',
    code: body.code,
    data: body.data,
    status: response?.status,
    raw: body,
  }
}

export function normalizePageData(data = {}) {
  const page = Number(data.page ?? data.current ?? 1)
  const size = Number(data.size ?? 20)
  const total = Number(data.total ?? 0)
  const pages = Number(data.pages ?? (size > 0 ? Math.ceil(total / size) : 0))

  return {
    items: Array.isArray(data.items) ? data.items : [],
    pagination: {
      page,
      size,
      total,
      pages,
    },
    raw: data,
  }
}

export function createApiError(error) {
  const raw = error?.raw ?? error
  const response = error?.response
  const responseBody = error?.data ?? response?.data
  const businessBody = isApiEnvelope(responseBody) ? responseBody : null

  return {
    message: businessBody?.message ?? error?.message ?? responseBody?.message ?? '请求失败',
    status: error?.status ?? response?.status,
    code: businessBody?.code ?? error?.code,
    data: businessBody?.data ?? error?.data,
    nonStandard: Boolean(error?.nonStandard),
    raw,
  }
}

export function isBusinessCode(error, code) {
  return createApiError(error).code === code
}

function isApiEnvelope(value) {
  return value && Object.prototype.hasOwnProperty.call(value, 'code')
    && Object.prototype.hasOwnProperty.call(value, 'message')
    && Object.prototype.hasOwnProperty.call(value, 'data')
}
