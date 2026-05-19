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
  })
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return createViteConfig({ ...process.env, ...env })
})
