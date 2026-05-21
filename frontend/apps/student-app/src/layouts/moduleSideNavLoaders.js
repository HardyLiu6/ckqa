import { resolveModule } from '@/composables/useCurrentModule'

const moduleSideNavPreloadCache = new Map()

// ModuleLayout 与顶栏预加载共用同一组 loader，避免首次进模块时副导航再冷启动。
export const moduleSideNavLoaders = {
  course: () => import('@/components/module-nav/CourseSideNav.vue'),
  qa: () => import('@/components/module-nav/QASideNav.vue'),
  knowledge: () => import('@/components/module-nav/KnowledgeSideNav.vue'),
  user: () => import('@/components/module-nav/UserSideNav.vue'),
}

export function resolveModuleSideNavKey(path) {
  const moduleKey = resolveModule(path)
  return moduleSideNavLoaders[moduleKey] ? moduleKey : ''
}

export function preloadModuleSideNavByPath(path) {
  const moduleKey = resolveModuleSideNavKey(path)
  if (!moduleKey) return Promise.resolve(null)

  if (!moduleSideNavPreloadCache.has(moduleKey)) {
    const preloadPromise = moduleSideNavLoaders[moduleKey]()
      .catch((error) => {
        moduleSideNavPreloadCache.delete(moduleKey)
        throw error
      })
    moduleSideNavPreloadCache.set(moduleKey, preloadPromise)
  }

  return moduleSideNavPreloadCache.get(moduleKey)
}
