<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  state: {
    type: String,
    default: '',
  },
})

const route = useRoute()

const currentState = computed(() => props.state || route.meta.routeState || 'coming-soon')

const stateCopy = {
  forbidden: {
    title: '无权限',
    message: '当前身份不能访问该页面。',
    action: '返回工作台',
    to: '/app/dashboard',
  },
  'not-found': {
    title: '页面不存在',
    message: '目标路由没有匹配的页面。',
    action: '返回工作台',
    to: '/app/dashboard',
  },
  'server-error': {
    title: '服务器错误',
    message: '页面进入异常状态。',
    action: '返回工作台',
    to: '/app/dashboard',
  },
  'coming-soon': {
    title: '未开放',
    message: '该页面已预留在路由结构中，当前阶段暂不实施。',
    action: '返回工作台',
    to: '/app/dashboard',
  },
}

const copy = computed(() => stateCopy[currentState.value] ?? stateCopy['coming-soon'])
</script>

<template>
  <section class="route-state">
    <p class="eyebrow">{{ currentState }}</p>
    <h1>{{ copy.title }}</h1>
    <p>{{ copy.message }}</p>
    <RouterLink class="primary-button" :to="copy.to">{{ copy.action }}</RouterLink>
  </section>
</template>
