import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_API_BASE_URL,
  DEFAULT_API_TIMEOUT,
  DEFAULT_TIMEOUT_ERROR_MESSAGE,
  buildErrorMessage,
  createRequestRuntime,
  resolveResponsePayload,
} from '../src/axios/config.js'

test('createRequestRuntime 在环境变量缺失时回退到默认值', () => {
  assert.deepEqual(createRequestRuntime({}), {
    baseURL: DEFAULT_API_BASE_URL,
    timeout: DEFAULT_API_TIMEOUT,
  })
})

test('createRequestRuntime 会规范化 baseURL 并解析 timeout', () => {
  assert.deepEqual(
    createRequestRuntime({
      VITE_API_BASE_URL: ' https://api.ckqa.local/ ',
      VITE_API_TIMEOUT: '15000',
    }),
    {
      baseURL: 'https://api.ckqa.local',
      timeout: 15000,
    },
  )
})

test('resolveResponsePayload 会解包常见的 data 包装结构', () => {
  assert.deepEqual(
    resolveResponsePayload({
      code: 0,
      message: 'ok',
      data: {
        items: [1, 2, 3],
      },
    }),
    {
      items: [1, 2, 3],
    },
  )
})

test('buildErrorMessage 优先使用后端消息并处理超时错误', () => {
  assert.equal(
    buildErrorMessage({
      response: {
        data: {
          message: '服务暂时不可用',
        },
      },
    }),
    '服务暂时不可用',
  )

  assert.equal(
    buildErrorMessage({
      code: 'ECONNABORTED',
      message: 'timeout of 10000ms exceeded',
    }),
    DEFAULT_TIMEOUT_ERROR_MESSAGE,
  )
})
