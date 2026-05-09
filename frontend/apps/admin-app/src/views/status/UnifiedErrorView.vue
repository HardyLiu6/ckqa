<script setup>
import { computed } from 'vue'
import { Gauge, HeartPulse, RefreshCw } from 'lucide-vue-next'
import { useRoute } from 'vue-router'

import { authStore } from '../../stores/auth.js'

const props = defineProps({
  status: {
    type: Number,
    default: 500,
  },
})

const route = useRoute()

function toQueryString(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join('、')
  return value || ''
}

const currentUser = computed(() => authStore.state.currentUser)
const identityLabel = computed(() => currentUser.value?.name || '未登录身份')
const dataScope = computed(() => currentUser.value?.dataScope || '未分配数据范围')
const requiredPermissions = computed(() => toQueryString(route.query.required) || '当前路由所需权限')

const copy = computed(() => {
  if (props.status === 403) {
    return {
      eyebrow: '403 / Forbidden',
      title: '当前身份无权访问',
      message: toQueryString(route.query.message) || '当前账号没有访问此页面所需的权限，请切换身份或返回工作台。',
    }
  }

  if (props.status === 404) {
    return {
      eyebrow: '404 / Not Found',
      title: '页面不存在',
      message: toQueryString(route.query.message) || '目标地址没有匹配到智课问答管理台页面。',
    }
  }

  return {
    eyebrow: '500 / Server Error',
    title: '页面进入异常状态',
    message: toQueryString(route.query.message) || '可以先刷新当前页面；如果仍然异常，请进入系统健康页查看 Java 编排与下游服务状态。',
  }
})

function refreshPage() {
  window.location.reload()
}
</script>

<template>
  <section class="route-state" :data-error-status="props.status">
    <p class="eyebrow">{{ copy.eyebrow }}</p>
    <h1>{{ copy.title }}</h1>
    <el-alert
      class="route-state__alert"
      :type="props.status === 403 ? 'warning' : 'error'"
      show-icon
      :closable="false"
      :title="copy.message"
    />

    <dl v-if="props.status === 403" class="route-state__facts">
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

    <div class="button-row">
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        tag="router-link"
        to="/app/dashboard"
      >
        <Gauge class="button-icon" :size="16" aria-hidden="true" />
        返回工作台
      </el-button>
      <el-button
        v-if="props.status === 500"
        class="ckqa-el-button ckqa-el-button--secondary"
        native-type="button"
        @click="refreshPage"
      >
        <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
        刷新页面
      </el-button>
      <el-button
        v-if="props.status === 500"
        class="ckqa-el-button ckqa-el-button--secondary"
        tag="router-link"
        to="/app/health"
      >
        <HeartPulse class="button-icon" :size="16" aria-hidden="true" />
        系统健康
      </el-button>
    </div>
  </section>
</template>
