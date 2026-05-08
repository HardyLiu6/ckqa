import { createRouter, createWebHistory } from 'vue-router'
import { markRaw } from 'vue'

import AuthLayout from '../layouts/AuthLayout.vue'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import DetailLayout from '../layouts/DetailLayout.vue'
import WorkflowLayout from '../layouts/WorkflowLayout.vue'
import LoginView from '../views/auth/LoginView.vue'
import DashboardPage from '../views/dashboard/DashboardPage.vue'
import HealthView from '../views/system/HealthView.vue'
import ModulePage from '../views/pages/ModulePage.vue'
import RouteState from '../views/status/RouteState.vue'
import UnifiedErrorView from '../views/status/UnifiedErrorView.vue'
import { getAdminPinia } from '../stores/pinia.js'
import { useAuthStore } from '../stores/auth.js'
import { routeRecords } from './routes.js'

const componentMap = {
  LoginView,
  DashboardPage,
  HealthView,
  ModulePage,
  RouteState,
  UnifiedErrorView,
}

const layoutMap = {
  auth: markRaw(AuthLayout),
  console: markRaw(ConsoleLayout),
  detail: markRaw(DetailLayout),
  workflow: markRaw(WorkflowLayout),
}

function toVueRoute(record) {
  const route = {
    ...record,
    meta: {
      ...record.meta,
      layoutComponent: layoutMap[record.meta?.layout] ?? ConsoleLayout,
    },
  }

  if (record.componentKey) {
    route.component = componentMap[record.componentKey]
  }

  return route
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/app/dashboard' },
    ...routeRecords.map(toVueRoute),
    {
      path: '/:pathMatch(.*)*',
      redirect: '/404',
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(getAdminPinia())

  if (to.meta.public) {
    if (to.path === '/login' && authStore.state.isAuthenticated) {
      return routeTargetAfterLogin(to.query.redirect)
    }
    return true
  }

  if (!authStore.state.isAuthenticated && authStore.state.token) {
    try {
      await authStore.loadCurrentUser()
    } catch {
      return {
        path: '/login',
        query: { redirect: to.fullPath },
      }
    }
  }

  if (!authStore.state.isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (!authStore.canAccess(to.meta.permissions)) {
    return '/403'
  }

  return true
})

function routeTargetAfterLogin(redirect) {
  return typeof redirect === 'string' && redirect.startsWith('/app') ? redirect : '/app/dashboard'
}

export default router
