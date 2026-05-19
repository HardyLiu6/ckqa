import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createViteConfig,
  resolveApiProxyTarget,
} from '../vite.config.js'

test('开发服务器把同源 /api/v1 代理到 Java 后端', () => {
  assert.equal(resolveApiProxyTarget({}), 'http://127.0.0.1:8080')
  assert.equal(
    resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://backend.local:18080/' }),
    'http://backend.local:18080',
  )

  const devConfig = createViteConfig({})

  assert.equal(devConfig.server.port, 5174)
  assert.equal(devConfig.server.proxy['/api/v1'].target, 'http://127.0.0.1:8080')
  assert.equal(devConfig.server.proxy['/api/v1'].changeOrigin, true)
})
