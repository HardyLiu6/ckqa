import { createRouter, createWebHistory } from 'vue-router'
import { markRaw } from 'vue'

import AuthLayout from '../layouts/AuthLayout.vue'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import DetailLayout from '../layouts/DetailLayout.vue'
import WorkflowLayout from '../layouts/WorkflowLayout.vue'
import LoginView from '../views/auth/LoginView.vue'
import DashboardView from '../views/dashboard/DashboardView.vue'
import HealthView from '../views/system/HealthView.vue'
import ModulePage from '../views/pages/ModulePage.vue'
import RouteState from '../views/status/RouteState.vue'
import { getAdminPinia } from '../stores/pinia.js'
import { useAuthStore } from '../stores/auth.js'
import { routeRecords } from './routes.js'

const componentMap = {
  LoginView,
  DashboardView,
  HealthView,
  ModulePage,
  RouteState,
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

router.beforeEach((to) => {
  const authStore = useAuthStore(getAdminPinia())

  if (to.meta.public) {
    return true
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

export default router
