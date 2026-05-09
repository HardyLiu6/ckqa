<script setup>
import { computed } from 'vue'

import ScopeChip from './ScopeChip.vue'
import ThemeToggle from './ThemeToggle.vue'
import NotificationDropdown from './NotificationDropdown.vue'
import CkCommandPalette from './CkCommandPalette.vue'
import { BRAND } from '../../copy/brand.js'

const props = defineProps({
  apiBaseUrl: { type: String, default: '' },
  currentUser: { type: Object, default: () => null },
  dataScopeLabel: { type: String, default: '未登录' },
  commandGroups: { type: Array, default: () => [] },
})

const emit = defineEmits(['logout'])

const initial = computed(() => {
  const name = props.currentUser?.name || props.currentUser?.username || ''
  return name.charAt(0).toUpperCase() || '·'
})

function openCommandPalette() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true }))
}
</script>

<template>
  <header class="app-topbar">
    <div class="app-topbar-left">
      <RouterLink class="app-topbar-logo" to="/app/dashboard">
        <span class="app-topbar-mark" aria-hidden="true" />
        <span>{{ BRAND.name }}</span>
      </RouterLink>
      <ScopeChip />
    </div>

    <div class="app-topbar-right">
      <button
        class="app-topbar-cmd-trigger"
        type="button"
        title="命令面板（⌘K / Ctrl+K）"
        aria-label="打开命令面板"
        @click="openCommandPalette"
      >
        <span aria-hidden="true">⌘K</span>
      </button>
      <ThemeToggle />
      <NotificationDropdown />
      <button
        v-if="currentUser"
        class="app-topbar-avatar identity-avatar"
        type="button"
        :title="currentUser.name || currentUser.username || dataScopeLabel"
        :aria-label="`${currentUser.name || currentUser.username || ''} · 退出登录`"
        @click="emit('logout')"
      >
        {{ initial }}
      </button>
    </div>

    <CkCommandPalette :groups="commandGroups" />
  </header>
</template>

<style scoped lang="scss">
.app-topbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px;
  height: 52px;
  padding: 0 18px;
  background: var(--ckqa-surface);
  border-bottom: 1px solid var(--ckqa-border);
  position: sticky; top: 0; z-index: 20;
}
.app-topbar-left,
.app-topbar-right { display: flex; align-items: center; gap: 12px; }
.app-topbar-logo {
  display: inline-flex; align-items: center; gap: 9px;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-semibold);
  color: var(--ckqa-text);
  text-decoration: none;
}
.app-topbar-mark {
  width: 22px; height: 22px;
  background: linear-gradient(135deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  border-radius: var(--ckqa-radius-md);
  box-shadow: 0 2px 6px rgb(217 119 87 / 30%);
}
.app-topbar-cmd-trigger {
  height: 28px; padding: 0 9px;
  font-size: var(--ckqa-text-xs-size);
  background: var(--ckqa-surface-muted);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text-muted);
  cursor: pointer;
  font-family: var(--ckqa-font-mono);
}
.app-topbar-cmd-trigger:hover { background: var(--ckqa-surface-strong); color: var(--ckqa-text); }
.app-topbar-avatar {
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #c4ad8b, #8d6e54);
  color: white;
  border: none;
  border-radius: var(--ckqa-radius-full);
  font-size: var(--ckqa-text-xs-size);
  font-weight: var(--ckqa-fw-medium);
  cursor: pointer;
}
</style>
