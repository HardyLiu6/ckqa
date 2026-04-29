import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export function resolveApiProxyTarget(env = process.env) {
  const rawTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return rawTarget.trim().replace(/\/+$/, '')
}

export function createViteConfig(env = process.env) {
  return defineConfig({
    plugins: [
      vue(),
      AutoImport({ resolvers: [ElementPlusResolver()] }),
      Components({ resolvers: [ElementPlusResolver()] }),
    ],
    server: {
      proxy: {
        '/api/v1': {
          target: resolveApiProxyTarget(env),
          changeOrigin: true,
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
