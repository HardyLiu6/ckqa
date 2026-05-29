import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createViteConfig,
  resolveManualChunk,
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

test('构建时把图谱和通用框架依赖拆到独立 chunk，避免入口 chunk 过大', () => {
  assert.equal(resolveManualChunk('/repo/node_modules/vue/dist/vue.runtime.esm-bundler.js'), 'vendor-vue')
  assert.equal(resolveManualChunk('/repo/node_modules/element-plus/es/index.mjs'), 'vendor-element')
  assert.equal(resolveManualChunk('/repo/node_modules/@antv/g6/esm/index.js'), 'graph-g6')
  assert.equal(resolveManualChunk('/repo/node_modules/@antv/g/esm/index.js'), 'graph-render')
  assert.equal(resolveManualChunk('/repo/node_modules/@antv/layout/esm/index.js'), 'graph-layout')
  assert.equal(resolveManualChunk('/repo/node_modules/d3-force/src/index.js'), 'graph-layout')
  assert.equal(resolveManualChunk('/repo/node_modules/@vue/runtime-core/dist/runtime-core.esm-bundler.js'), 'vendor-vue')
  assert.equal(resolveManualChunk('/repo/node_modules/lodash-es/debounce.js'), 'vendor-lodash')
})
