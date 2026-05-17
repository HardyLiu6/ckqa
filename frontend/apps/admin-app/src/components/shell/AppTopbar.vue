<script setup>
import { computed, ref, watch } from 'vue'
import {
  ChevronDown,
  LogOut,
  Search,
  Server,
  ShieldCheck,
  UserCog,
} from 'lucide-vue-next'
import { RouterLink, useRouter } from 'vue-router'

import ThemeControl from './ThemeControl.vue'

const props = defineProps({
  apiBaseUrl: { type: String, required: true },
  currentUser: { type: Object, default: null },
  dataScopeLabel: { type: String, default: '未登录' },
})

const emit = defineEmits(['logout'])
const router = useRouter()
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
const identityLabel = computed(() => props.currentUser?.displayName || props.currentUser?.name || '未登录')
const username = computed(() => props.currentUser?.username || '')
const avatarUrl = computed(() => props.currentUser?.avatarUrl || '')
const identityInitial = computed(() => identityLabel.value.trim().charAt(0) || 'U')

watch(avatarUrl, () => {
  avatarLoadFailed.value = false
})

function handleDropdownCommand(cmd) {
  if (cmd === 'profile') {
    router.push({ name: 'profile' })
  } else if (cmd === 'logout') {
    emit('logout')
  }
}
</script>

<template>
  <header class="app-topbar">
    <slot name="prepend" />
    <RouterLink class="brand" to="/app/dashboard" aria-label="返回工作台">
      <img class="brand-mark" src="/logo.png" alt="智课问答" />
      <span>
        <strong>智课问答</strong>
        <small>课程资料 · 知识库 · 智能问答</small>
      </span>
    </RouterLink>

    <!-- 搜索（待接入）：保持视觉占位但缩成 chip 风格，让出空间给主题/身份 -->
    <span class="topbar-search-chip" title="全局搜索（功能尚未接入）">
      <Search :size="14" aria-hidden="true" />
      <em>搜索待接入</em>
    </span>

    <span class="runtime-chip" title="当前请求基线">
      <Server :size="15" aria-hidden="true" />
      <strong>{{ apiBaseline }}</strong>
    </span>

    <ThemeControl />

    <!-- 身份 dropdown：头像按钮 + 下拉菜单（含个人中心 / 退出） -->
    <el-dropdown
      class="identity-dropdown"
      trigger="click"
      :hide-on-click="true"
      @command="handleDropdownCommand"
    >
      <button
        type="button"
        class="identity-trigger"
        :title="`${identityLabel}（${dataScopeLabel}）`"
        aria-label="账号菜单"
      >
        <span class="identity-avatar" aria-hidden="true">
          <img
            v-if="avatarUrl && !avatarLoadFailed"
            :src="avatarUrl"
            :alt="`${identityLabel}头像`"
            @error="avatarLoadFailed = true"
          />
          <span v-else>{{ identityInitial }}</span>
        </span>
        <span class="identity-trigger__copy">
          <strong>{{ identityLabel }}</strong>
          <small>{{ dataScopeLabel }}</small>
        </span>
        <ChevronDown :size="14" class="identity-trigger__chevron" aria-hidden="true" />
      </button>
      <template #dropdown>
        <el-dropdown-menu class="identity-menu">
          <header class="identity-menu__header">
            <span class="identity-avatar identity-avatar--lg" aria-hidden="true">
              <img
                v-if="avatarUrl && !avatarLoadFailed"
                :src="avatarUrl"
                :alt="`${identityLabel}头像`"
              />
              <span v-else>{{ identityInitial }}</span>
            </span>
            <span class="identity-menu__copy">
              <strong>{{ identityLabel }}</strong>
              <small v-if="username">@{{ username }}</small>
              <span class="identity-menu__scope">
                <ShieldCheck :size="12" aria-hidden="true" />
                {{ dataScopeLabel }}
              </span>
            </span>
          </header>
          <el-dropdown-item command="profile" :icon="UserCog">
            个人中心
          </el-dropdown-item>
          <el-dropdown-item command="logout" :icon="LogOut" divided>
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>

<style scoped>
.topbar-search-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px dashed var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-muted);
  font-size: 12px;
  cursor: not-allowed;
  user-select: none;
  white-space: nowrap;
}
.topbar-search-chip em {
  font-style: normal;
}

.identity-dropdown {
  display: inline-flex;
  align-items: center;
}

.identity-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  height: 36px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full, 999px);
  background: color-mix(in srgb, var(--ckqa-surface) 88%, transparent);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
  font: inherit;
  color: var(--ckqa-text);
}
.identity-trigger:hover {
  border-color: var(--ckqa-accent);
  background: color-mix(in srgb, var(--ckqa-accent) 8%, var(--ckqa-surface));
}
.identity-trigger:focus-visible {
  outline: 2px solid var(--ckqa-accent);
  outline-offset: 2px;
}

.identity-trigger__copy {
  display: inline-flex;
  flex-direction: column;
  line-height: 1.1;
  text-align: left;
  min-width: 0;
}
.identity-trigger__copy strong {
  font-size: 13px;
  font-weight: 600;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ckqa-text);
}
.identity-trigger__copy small {
  font-size: 11px;
  color: var(--ckqa-text-muted);
}
.identity-trigger__chevron {
  color: var(--ckqa-text-muted);
  flex-shrink: 0;
}

.identity-avatar--lg {
  width: 40px;
  height: 40px;
  font-size: 16px;
}
</style>

<style>
/* 全局：dropdown 弹层菜单头部样式（el-dropdown 弹层会被 teleport 出当前 scope，
   因此放在非 scoped style 中） */
.identity-menu .identity-menu__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--ckqa-border);
  margin-bottom: 4px;
  min-width: 200px;
}
.identity-menu .identity-menu__copy {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.2;
}
.identity-menu .identity-menu__copy strong {
  font-size: 14px;
  font-weight: 700;
  color: var(--ckqa-text);
}
.identity-menu .identity-menu__copy small {
  font-size: 11px;
  color: var(--ckqa-text-muted);
  font-family: var(--ckqa-font-mono);
}
.identity-menu .identity-menu__scope {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--ckqa-accent-strong);
}
</style>
