// 根据路由解析当前所属模块 + 提供模块色

import { computed } from 'vue'
import { useRoute } from 'vue-router'

// 模块 → 主色三档（和 styles/tokens/_colors.scss 保持严格一致）
export const MODULE_COLORS = {
  home: { 50: '#eef2ff', 500: '#6366f1', 700: '#4338ca' },
  course: { 50: '#eff6ff', 500: '#2563eb', 700: '#1d4ed8' },
  qa: { 50: '#faf5ff', 500: '#9333ea', 700: '#7e22ce' },
  knowledge: { 50: '#f0fdfa', 500: '#0d9488', 700: '#0f766e' },
  community: { 50: '#fff7ed', 500: '#ea580c', 700: '#c2410c' },
  analysis: { 50: '#fdf2f8', 500: '#db2777', 700: '#be185d' },
  user: { 50: '#f8fafc', 500: '#64748b', 700: '#334155' },
  landing: { 50: '#eef2ff', 500: '#6366f1', 700: '#4338ca' },
}

// 路径前缀 → 模块 key
const PREFIX_MAP = [
  ['/home', 'home'],
  ['/course', 'course'],
  ['/qa', 'qa'],
  ['/knowledge', 'knowledge'],
  ['/community', 'community'],
  ['/analysis', 'analysis'],
  ['/user', 'user'],
]

/**
 * 纯函数：根据路径返回模块 key
 * @param {string} path
 * @returns {'landing'|'home'|'course'|'qa'|'knowledge'|'community'|'analysis'|'user'}
 */
export function resolveModule(path) {
  if (path === '/' || path === '') return 'landing'
  for (const [prefix, key] of PREFIX_MAP) {
    if (path === prefix || path.startsWith(prefix + '/')) return key
  }
  return 'home'
}

/**
 * Composable：响应式拿到当前模块 key 与色卡
 */
export function useCurrentModule() {
  const route = useRoute()
  const moduleKey = computed(() => resolveModule(route.path))
  const colors = computed(() => MODULE_COLORS[moduleKey.value] || MODULE_COLORS.home)
  return { moduleKey, colors }
}
