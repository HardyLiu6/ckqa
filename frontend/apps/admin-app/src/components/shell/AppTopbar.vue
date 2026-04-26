<script setup>
import { computed } from 'vue'
import { LogOut, Search, Server } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

import ThemeControl from './ThemeControl.vue'

const props = defineProps({
  apiBaseUrl: { type: String, required: true },
  currentUser: { type: Object, default: null },
  dataScopeLabel: { type: String, default: '未登录' },
})

const emit = defineEmits(['logout', 'role-change'])

function formatApiBaseline(apiBaseUrl) {
  try {
    const url = new URL(apiBaseUrl)
    return `API ${url.pathname || '/api/v1'} · 开发态`
  } catch {
    return `API ${apiBaseUrl} · 开发态`
  }
}

const apiBaseline = computed(() => formatApiBaseline(props.apiBaseUrl))
const identityLabel = computed(() => props.currentUser?.name || '未登录')

function handleRoleChange(event) {
  emit('role-change', event.target.value)
}
</script>

<template>
  <header class="app-topbar">
    <RouterLink class="brand" to="/app/dashboard" aria-label="返回工作台">
      <span class="brand-mark">CK</span>
      <span>
        <strong>CKQA 运维台</strong>
        <small>课程知识库构建与运维平台</small>
      </span>
    </RouterLink>

    <label class="topbar-search" aria-disabled="true">
      <Search :size="16" aria-hidden="true" />
      <input
        type="search"
        value="搜索待接入"
        aria-label="全局搜索待接入"
        aria-disabled="true"
        disabled
      />
    </label>

    <span class="runtime-chip" title="当前请求基线">
      <Server :size="15" aria-hidden="true" />
      <strong>{{ apiBaseline }}</strong>
    </span>

    <ThemeControl />

    <div class="identity-cluster">
      <span class="identity-chip" title="当前身份和数据范围">
        <strong>{{ identityLabel }}</strong>
        <span>{{ dataScopeLabel }}</span>
      </span>
      <label class="role-switch">
        <span>身份</span>
        <select :value="currentUser?.role" aria-label="切换开发态身份" @change="handleRoleChange">
          <option value="admin">平台管理员</option>
          <option value="teacher">教师</option>
        </select>
      </label>
    </div>

    <button class="plain-button" type="button" @click="emit('logout')">
      <LogOut :size="16" aria-hidden="true" />
      退出
    </button>
  </header>
</template>
