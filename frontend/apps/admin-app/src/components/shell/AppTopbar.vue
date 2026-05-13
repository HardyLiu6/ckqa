<script setup>
import { computed, ref, watch } from 'vue'
import { LogOut, Search, Server, ShieldCheck } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

import ThemeControl from './ThemeControl.vue'

const props = defineProps({
  apiBaseUrl: { type: String, required: true },
  currentUser: { type: Object, default: null },
  dataScopeLabel: { type: String, default: '未登录' },
})

const emit = defineEmits(['logout'])
const avatarLoadFailed = ref(false)

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
const avatarUrl = computed(() => props.currentUser?.avatarUrl || '')
const identityInitial = computed(() => identityLabel.value.trim().charAt(0) || 'U')

watch(avatarUrl, () => {
  avatarLoadFailed.value = false
})
</script>

<template>
  <header class="app-topbar">
    <slot name="prepend" />
    <RouterLink class="brand" to="/app/dashboard" aria-label="返回工作台">
      <span class="brand-mark">CK</span>
      <span>
        <strong>课程知识助手</strong>
        <small>课程资料 · 知识库 · 智能问答</small>
      </span>
    </RouterLink>

    <el-input
      class="topbar-search-input"
      model-value="搜索待接入"
      type="search"
      aria-disabled="true"
      disabled
      readonly
      aria-label="全局搜索待接入"
    >
      <template #prefix>
        <Search :size="16" aria-hidden="true" />
      </template>
    </el-input>

    <span class="runtime-chip" title="当前请求基线">
      <Server :size="15" aria-hidden="true" />
      <strong>{{ apiBaseline }}</strong>
    </span>

    <ThemeControl />

    <div class="identity-cluster">
      <span class="identity-chip" title="当前身份和数据范围">
        <span class="identity-avatar" aria-hidden="true">
          <img
            v-if="avatarUrl && !avatarLoadFailed"
            :src="avatarUrl"
            :alt="`${identityLabel}头像`"
            @error="avatarLoadFailed = true"
          />
          <span v-else>{{ identityInitial }}</span>
        </span>
        <ShieldCheck :size="15" aria-hidden="true" />
        <strong>{{ identityLabel }}</strong>
        <span>{{ dataScopeLabel }}</span>
      </span>
    </div>

    <el-button
      class="ckqa-el-button ckqa-el-button--ghost"
      native-type="button"
      @click="emit('logout')"
    >
      <LogOut class="button-icon" :size="16" aria-hidden="true" />
      退出
    </el-button>
  </header>
</template>
