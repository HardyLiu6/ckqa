<script setup>
import { computed } from 'vue'
import { Gauge, HeartPulse, RefreshCw } from 'lucide-vue-next'
import { useRoute } from 'vue-router'

import { authStore } from '../../stores/auth.js'

const props = defineProps({
  state: {
    type: String,
    default: '',
  },
})

const route = useRoute()

const currentState = computed(() => props.state || route.meta.routeState || 'coming-soon')

const navGroupLabels = {
  dashboard: '工作台',
  courses: '课程与资料',
  knowledge: '知识库构建',
  qa: '问答运维',
  users: '用户与权限',
  system: '系统与审计',
}

const statusLabels = {
  mvp: 'MVP 已开放',
  upcoming: '已规划，待接入',
  skeleton: '页面骨架',
}

function toQueryString(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join('、')
  return value || ''
}

const requiredPermissions = computed(() => {
  const queryRequired = toQueryString(route.query.required)
  if (queryRequired) return queryRequired

  const metaPermissions = route.meta.permissions || []
  if (metaPermissions.length) return metaPermissions.join('、')

  return '当前路由所需权限'
})

const currentUser = computed(() => authStore.state.currentUser)

const identityLabel = computed(() => currentUser.value?.name || '未登录身份')
const dataScope = computed(() => currentUser.value?.dataScope || '未分配数据范围')

const moduleLabel = computed(() => {
  const queryModule = toQueryString(route.query.module)
  if (queryModule) return queryModule

  // 列表路由 `/app/retrieval-logs` 的占位文案沿用运维导航分组的显式别名，
  // 详情路由 `retrieval-log-detail` 继续走 navGroup 兜底逻辑。
  if (route.name === 'retrieval-logs') return '运维 · 检索日志'

  return navGroupLabels[route.meta.navGroup] || '当前模块'
})

const routeTitle = computed(() => toQueryString(route.query.title) || route.meta.title || '预留页面')
const planningStatus = computed(() => {
  const queryStatus = toQueryString(route.query.status)
  if (queryStatus) return queryStatus

  return statusLabels[route.meta.status] || '已纳入路由规划'
})

const copy = computed(() => {
  if (currentState.value === 'forbidden') {
    return {
      eyebrow: '403 / Forbidden',
      title: '当前身份无权访问',
      message: '路由守卫已拦截本次访问，请切换到具备权限的身份或返回工作台。',
    }
  }

  if (currentState.value === 'not-found') {
    return {
      eyebrow: '404 / Not Found',
      title: '页面不存在',
      message: '目标地址没有匹配到智课问答管理台页面。',
    }
  }

  if (currentState.value === 'server-error') {
    return {
      eyebrow: '500 / Server Error',
      title: '页面进入异常状态',
      message: '可以先刷新当前页面；如果仍然异常，请进入系统健康页查看 Java 编排与下游服务状态。',
    }
  }

  return {
    eyebrow: 'Coming Soon',
    title: routeTitle.value,
    message: '该入口已保留在导航和权限结构中，当前阶段暂不开放业务页面。',
  }
})

function refreshPage() {
  window.location.reload()
}
</script>

<template>
  <section class="route-state">
    <figure class="ck-route-state-illustration" aria-hidden="true" />

    <p class="eyebrow">{{ copy.eyebrow }}</p>
    <h1>{{ copy.title }}</h1>
    <p>{{ copy.message }}</p>

    <dl v-if="currentState === 'forbidden'" class="route-state__facts">
      <div>
        <dt>当前身份</dt>
        <dd>{{ identityLabel }}</dd>
      </div>
      <div>
        <dt>数据范围</dt>
        <dd>{{ dataScope }}</dd>
      </div>
      <div>
        <dt>缺失权限</dt>
        <dd>{{ requiredPermissions }}</dd>
      </div>
    </dl>

    <dl v-else-if="currentState === 'coming-soon'" class="route-state__facts">
      <div>
        <dt>所属模块</dt>
        <dd>{{ moduleLabel }}</dd>
      </div>
      <div>
        <dt>规划状态</dt>
        <dd>{{ planningStatus }}</dd>
      </div>
      <div>
        <dt>路由名称</dt>
        <dd>{{ route.name || route.path }}</dd>
      </div>
    </dl>

    <div class="button-row">
      <el-button type="primary" tag="router-link" to="/app/dashboard">
        <Gauge class="button-icon" :size="16" aria-hidden="true" />
        返回工作台
      </el-button>
      <el-button
        v-if="currentState === 'server-error'"
        native-type="button"
        @click="refreshPage"
      >
        <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
        刷新页面
      </el-button>
      <el-button
        v-if="currentState === 'server-error'"
        tag="router-link"
        to="/app/health"
      >
        <HeartPulse class="button-icon" :size="16" aria-hidden="true" />
        系统健康
      </el-button>
    </div>
  </section>
</template>

<style scoped lang="scss">
/*
 * RouteState 视觉层：只做顶部品牌图形与局部布局，颜色与圆角全部取自 M1 Token。
 * 按钮完全交给 Element Plus 主题映射（styles/element-plus.scss）接管，不再定义
 * .ckqa-el-button / .ckqa-el-button--primary 等自定义样式段。
 */

.route-state {
  position: relative;
  overflow: hidden;
  background: var(--ckqa-surface);
}

.ck-route-state-illustration {
  width: 100%;
  height: 120px;
  margin: 0 0 4px;
  border-radius: var(--ckqa-radius-md);
  border: 1px solid var(--ckqa-border-soft);
  background:
    radial-gradient(
      circle at 20% 30%,
      var(--ckqa-accent-soft) 0%,
      transparent 60%
    ),
    radial-gradient(
      circle at 80% 70%,
      var(--ckqa-accent-soft) 0%,
      transparent 55%
    ),
    var(--ckqa-bg-elevated);
  pointer-events: none;
}

.button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

@media (prefers-reduced-motion: reduce) {
  .ck-route-state-illustration {
    background:
      radial-gradient(
        circle at 50% 50%,
        var(--ckqa-accent-soft) 0%,
        transparent 65%
      ),
      var(--ckqa-bg-elevated);
  }
}

@media (max-width: 560px) {
  .ck-route-state-illustration {
    height: 96px;
  }
}
</style>
