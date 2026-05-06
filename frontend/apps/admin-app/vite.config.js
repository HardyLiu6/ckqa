import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const VENDOR_CHUNK_RULES = [
  { name: 'vendor-vue', packages: ['vue', 'vue-router', 'pinia'] },
  { name: 'vendor-icons', packages: ['@element-plus/icons-vue', 'lucide-vue-next'] },
  { name: 'vendor-element-plus', packages: ['element-plus', '@element-plus'] },
  { name: 'vendor-http', packages: ['axios'] },
]

export function resolveApiProxyTarget(env = process.env) {
  const rawTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return rawTarget.trim().replace(/\/+$/, '')
}

export function resolveAdminAppManualChunk(id) {
  const normalizedId = id.replaceAll('\\', '/')
  const nodeModulesMarker = '/node_modules/'
  if (!normalizedId.includes(nodeModulesMarker)) {
    return null
  }

  const packagePath = normalizedId.split(nodeModulesMarker).pop()
  const packageName = packagePath.startsWith('@')
    ? packagePath.split('/').slice(0, 2).join('/')
    : packagePath.split('/')[0]

  const matchedRule = VENDOR_CHUNK_RULES.find((rule) => (
    rule.packages.some((packagePrefix) => (
      packageName === packagePrefix || packageName.startsWith(`${packagePrefix}/`)
    ))
  ))

  return matchedRule?.name ?? 'vendor-misc'
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
    build: {
      rolldownOptions: {
        output: {
          codeSplitting: true,
          manualChunks: resolveAdminAppManualChunk,
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
