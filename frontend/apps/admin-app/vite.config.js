import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const VENDOR_CHUNK_RULES = [
  { name: 'vendor-vue', packages: ['vue', 'vue-router', 'pinia'] },
  { name: 'vendor-icons', packages: ['@element-plus/icons-vue', 'lucide-vue-next'] },
  { name: 'vendor-http', packages: ['axios'] },
]

const ELEMENT_PLUS_COMPONENT_CHUNKS = [
  {
    name: 'vendor-element-forms',
    components: [
      'autocomplete',
      'cascader',
      'checkbox',
      'checkbox-group',
      'color-picker',
      'date-picker',
      'form',
      'input',
      'input-number',
      'option',
      'option-group',
      'radio',
      'radio-group',
      'rate',
      'select',
      'slider',
      'switch',
      'time-picker',
      'time-select',
      'transfer',
      'upload',
    ],
  },
  {
    name: 'vendor-element-data',
    components: [
      'calendar',
      'carousel',
      'descriptions',
      'empty',
      'image',
      'pagination',
      'result',
      'skeleton',
      'statistic',
      'table',
      'table-v2',
      'timeline',
      'tree',
      'tree-select',
      'virtual-list',
    ],
  },
  {
    name: 'vendor-element-feedback',
    components: [
      'alert',
      'dialog',
      'drawer',
      'loading',
      'message',
      'message-box',
      'notification',
      'popconfirm',
      'popover',
      'progress',
      'tooltip',
    ],
  },
  {
    name: 'vendor-element-navigation',
    components: [
      'affix',
      'anchor',
      'backtop',
      'breadcrumb',
      'dropdown',
      'menu',
      'page-header',
      'steps',
      'tabs',
    ],
  },
]

function resolveElementPlusComponentChunk(packagePath) {
  const componentName = packagePath.match(/\/(?:es|lib)\/components\/([^/]+)/)?.[1]
  if (!componentName) {
    return 'vendor-element-components'
  }
  const matchedGroup = ELEMENT_PLUS_COMPONENT_CHUNKS.find((group) => (
    group.components.includes(componentName)
  ))
  return matchedGroup?.name ?? 'vendor-element-basic'
}

function resolveElementPlusChunk(packageName, packagePath) {
  if (packageName === '@element-plus/icons-vue') {
    return 'vendor-icons'
  }
  if (packageName.startsWith('@element-plus/')) {
    return 'vendor-element-core'
  }
  if (packageName !== 'element-plus') {
    return null
  }

  if (packagePath.includes('/es/components/') || packagePath.includes('/lib/components/')) {
    return resolveElementPlusComponentChunk(packagePath)
  }
  if (packagePath.includes('/es/locale') || packagePath.includes('/lib/locale')) {
    return 'vendor-element-locale'
  }
  return 'vendor-element-core'
}

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
  const elementPlusChunk = resolveElementPlusChunk(packageName, packagePath)
  if (elementPlusChunk) {
    return elementPlusChunk
  }

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
      // dev 期 watch 的文件越少越好，避免 ENOSPC（inotify 限额）。
      // e2e / playwright 报告 / dist / 后端 target 都不该触发热更新，
      // 这些目录只在脱机命令里跑（pnpm test:e2e / pnpm build），
      // 跟 dev server 完全独立。
      watch: {
        ignored: [
          '**/node_modules/**',
          '**/.git/**',
          '**/dist/**',
          '**/e2e/**',
          '**/test-results/**',
          '**/playwright-report/**',
          '**/coverage/**',
          // 仓库根目录下与前端无关的目录，避免 vite 越级 watch 拖累
          '**/backend/**/target/**',
          '**/graphrag_pipeline/**',
          '**/pdf_ingest/**',
          '**/infra/**',
          '**/.superpowers/**',
        ],
      },
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
