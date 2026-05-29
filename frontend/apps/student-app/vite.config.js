import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export function resolveApiProxyTarget(env = process.env) {
  const rawTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return rawTarget.trim().replace(/\/+$/, '')
}

export function resolveManualChunk(id = '') {
  const normalizedId = String(id).replace(/\\/g, '/')
  if (!normalizedId.includes('/node_modules/')) {
    return undefined
  }

  if (normalizedId.includes('/@antv/g6/')) {
    return 'graph-g6'
  }
  if (
    normalizedId.includes('/@antv/layout')
    || normalizedId.includes('/@antv/hierarchy')
    || normalizedId.includes('/@antv/graphlib')
    || normalizedId.includes('/dagre/')
    || normalizedId.includes('/graphlib/')
    || normalizedId.includes('/ml-matrix/')
    || normalizedId.includes('/d3-')
    || normalizedId.includes('/d3/')
  ) {
    return 'graph-layout'
  }
  if (
    normalizedId.includes('/@antv/')
    || normalizedId.includes('/@naoak/')
    || normalizedId.includes('/workerize-loader/')
  ) {
    return 'graph-render'
  }
  if (
    normalizedId.includes('/gl-matrix/')
    || normalizedId.includes('/html2canvas/')
    || normalizedId.includes('/comlink/')
    || normalizedId.includes('/bubblesets-js/')
    || normalizedId.includes('/eventemitter3/')
    || normalizedId.includes('/color-name/')
    || normalizedId.includes('/color-string/')
    || normalizedId.includes('/simple-swizzle/')
    || normalizedId.includes('/is-arrayish/')
  ) {
    return 'graph-extra'
  }
  if (
    normalizedId.includes('/vue/')
    || normalizedId.includes('/@vue/')
    || normalizedId.includes('/@vueuse/')
    || normalizedId.includes('/vue-router/')
    || normalizedId.includes('/pinia/')
  ) {
    return 'vendor-vue'
  }
  if (
    normalizedId.includes('/element-plus/')
    || normalizedId.includes('/@element-plus/')
    || normalizedId.includes('/@ctrl/tinycolor/')
    || normalizedId.includes('/@sxzz/popperjs-es/')
    || normalizedId.includes('/async-validator/')
    || normalizedId.includes('/entities/')
  ) {
    return 'vendor-element'
  }
  if (
    normalizedId.includes('/lodash/')
    || normalizedId.includes('/lodash-es/')
  ) {
    return 'vendor-lodash'
  }
  if (normalizedId.includes('/@babel/runtime/')) {
    return 'vendor-babel'
  }
  if (
    normalizedId.includes('/markdown-it/')
    || normalizedId.includes('/dompurify/')
    || normalizedId.includes('/linkify-it/')
    || normalizedId.includes('/mdurl/')
    || normalizedId.includes('/uc.micro/')
  ) {
    return 'vendor-markdown'
  }
  if (
    normalizedId.includes('/gsap/')
    || normalizedId.includes('/aos/')
    || normalizedId.includes('/lenis/')
  ) {
    return 'vendor-motion'
  }
  if (
    normalizedId.includes('/axios/')
    || normalizedId.includes('/@microsoft/fetch-event-source/')
  ) {
    return 'vendor-request'
  }
  return 'vendor-misc'
}

export function createViteConfig(env = process.env) {
  return defineConfig({
    plugins: [
      vue(),
      vueDevTools(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
    ],
    server: {
      host: '0.0.0.0',
      port: 5174,
      proxy: {
        '/api/v1': {
          target: resolveApiProxyTarget(env),
          changeOrigin: true,
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        '@@': fileURLToPath(new URL('./src/components', import.meta.url)),
      },
      extensions: ['.js', '.vue', '.json'],
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: resolveManualChunk,
        },
      },
    },
  })
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return createViteConfig({ ...process.env, ...env })
})
