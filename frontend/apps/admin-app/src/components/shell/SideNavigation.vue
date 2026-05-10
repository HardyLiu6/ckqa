<script setup>
// SideNavigation v3 —— 视觉打磨迭代（2026-05-09）。
// 展开态：图标 + 暖色 active rail（绝对定位） + 分组标题 + 底部状态卡。
// 折叠态：窄栏（64px），仅图标居中；toggle 按钮悬浮右边缘。
// 折叠状态与快捷键由 sidebar-collapse-model 管理（P4b）。

import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import SbIcon from './icons/SbIcon.vue'
import { authStore } from '../../stores/auth.js'
import { BRAND } from '../../copy/brand.js'
import {
  RAIL_WIDTH,
  computeRailTop,
  getRailHeight,
  shouldShowRail,
} from './active-rail-model.js'
import {
  COLLAPSED_SIDEBAR_WIDTH,
  EXPANDED_SIDEBAR_WIDTH,
  isToggleKey,
  readCollapsed,
  writeCollapsed,
} from './sidebar-collapse-model.js'

const props = defineProps({
  sections: { type: Array, required: true },
  sectionLabels: { type: Object, required: true },
  activePath: { type: String, default: '' },
})

const route = useRoute()

// 路由 key 到图标 name 的映射（key 来自 primaryNavigation）
const ICON_BY_KEY = {
  dashboard: 'dashboard',
  courses: 'book',
  materials: 'file',
  'knowledge-bases': 'database',
  'qa-sessions': 'chat',
  'retrieval-logs': 'list',
  'kb-validation': 'shield',
  users: 'users',
  health: 'heart',
  audit: 'file',
}

// ————— 折叠态 —————
// 视觉打磨迭代：折叠状态为局部 state，不进 Pinia
const collapsed = ref(readCollapsed())
provide('sidebarCollapsed', collapsed)

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  writeCollapsed(collapsed.value)
  // 同步 CSS 变量，驱动 ConsoleLayout main 区 padding-left
  syncSidebarWidthVar()
  // 折叠切换时 rail 需要等动画结束后再重算（见 5.x 动画期保护）
  scheduleRailRecompute({ delay: 360 })
}

function syncSidebarWidthVar() {
  if (typeof document === 'undefined') return
  const width = collapsed.value ? COLLAPSED_SIDEBAR_WIDTH : EXPANDED_SIDEBAR_WIDTH
  document.documentElement.style.setProperty('--sb-w', `${width}px`)
}

function onKeyDown(event) {
  if (!isToggleKey(event)) return
  event.preventDefault()
  toggleCollapsed()
}

// ————— Active rail —————
const listEl = ref(null)
const railTop = ref(0)
const railVisible = ref(false)
const isMounted = ref(false)

function recomputeRail() {
  if (typeof document === 'undefined') return
  const listNode = listEl.value
  if (!listNode) return
  const activeNode = listNode.querySelector('.sb-item.is-active')
  if (!activeNode) {
    railVisible.value = false
    return
  }
  const listRect = listNode.getBoundingClientRect()
  const activeRect = activeNode.getBoundingClientRect()
  if (!shouldShowRail(activeRect)) {
    railVisible.value = false
    return
  }
  railTop.value = computeRailTop(listRect, activeRect, collapsed.value)
  railVisible.value = true
}

function scheduleRailRecompute({ delay = 0 } = {}) {
  if (typeof window === 'undefined') return
  const run = () => nextTick().then(recomputeRail)
  if (delay > 0) setTimeout(run, delay)
  else run()
}

watch(
  () => route.path,
  () => scheduleRailRecompute(),
)

// ————— Lifecycle —————
onMounted(() => {
  syncSidebarWidthVar()
  if (typeof window !== 'undefined') {
    window.addEventListener('keydown', onKeyDown)
  }
  // 首次挂载等 DOM render 完，避免 rect 为 (0,0) 的初始抖动
  scheduleRailRecompute({ delay: 50 })
  isMounted.value = true
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKeyDown)
  }
})

const currentUser = computed(() => authStore.state.currentUser)
const userInitial = computed(() => {
  const name = currentUser.value?.name || currentUser.value?.username || ''
  return name.charAt(0).toUpperCase() || '·'
})
const userLabel = computed(() =>
  currentUser.value?.name || currentUser.value?.username || '未登录',
)
const roleLabel = computed(() => currentUser.value?.dataScope || '访客')

const railHeight = computed(() => getRailHeight(collapsed.value))
</script>

<template>
  <aside
    class="side-nav side-navigation"
    :class="{ sidebar: true, 'is-collapsed': collapsed }"
    aria-label="主导航"
    data-test-id="sidebar"
  >
    <!-- Brand row -->
    <header class="sb-brand">
      <span class="sb-brand-mark" aria-hidden="true">智</span>
      <div class="sb-brand-text">
        <strong class="sb-brand-name">{{ BRAND.name }}</strong>
        <span class="sb-brand-tagline">{{ BRAND.tagline }} · v{{ BRAND.version }}</span>
      </div>
      <button
        class="sb-toggle ck-pressable"
        type="button"
        :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'"
        :title="collapsed ? '展开侧栏 ⌘\\' : '折叠侧栏 ⌘\\'"
        data-test-id="sb-toggle"
        @click="toggleCollapsed"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" width="14" height="14">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
    </header>

    <!-- Nav list -->
    <div class="sb-body">
      <div class="sb-list" ref="listEl">
        <!-- Active rail -->
        <span
          v-show="railVisible"
          class="sb-rail"
          :style="{
            top: `${railTop}px`,
            height: `${railHeight}px`,
            width: `${RAIL_WIDTH}px`,
          }"
          data-test-id="active-rail"
          aria-hidden="true"
        />
        <template v-for="section in sections" :key="section.key">
          <h4
            v-if="sectionLabels[section.key]"
            class="sb-section-title"
          >
            {{ sectionLabels[section.key] }}
          </h4>
          <RouterLink
            v-for="item in section.items"
            :key="item.key"
            :to="item.path"
            class="sb-item side-nav-item"
            :class="{ 'is-active': activePath === item.path }"
            :data-test-id="`nav-${item.key}`"
            :title="collapsed ? item.label : ''"
          >
            <span class="sb-icon" aria-hidden="true">
              <SbIcon :name="ICON_BY_KEY[item.key] || 'dashboard'" />
            </span>
            <span class="sb-label side-nav-item-label">{{ item.label }}</span>
            <span v-if="item.count" class="sb-count side-nav-item-count">{{ item.count }}</span>
          </RouterLink>
        </template>
      </div>
    </div>

    <!-- Status card -->
    <div class="sb-status" v-if="currentUser">
      <span class="sb-avatar" aria-hidden="true">{{ userInitial }}</span>
      <div class="sb-identity">
        <strong class="sb-id-name">{{ userLabel }}</strong>
        <span class="sb-id-role">{{ roleLabel }}</span>
      </div>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.sidebar {
  position: sticky;
  top: 52px;
  align-self: start;
  width: 240px;
  height: calc(100vh - 52px);
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  background: var(--ckqa-surface);
  border-right: 1px solid var(--ckqa-border);
  overflow: visible;   /* 折叠态 toggle 需要溢出右边缘；active rail 需要溢出左边缘 */
  transition:
    width var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    padding var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

/* ————— Brand row ————— */
.sb-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 4px 12px;
  border-bottom: 1px solid var(--ckqa-border-soft);
  position: relative;
}

.sb-brand-mark {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  color: var(--ckqa-accent-contrast);
  font-family: var(--ckqa-font-sans);
  font-weight: var(--ckqa-fw-semibold);
  font-size: 16px;
  box-shadow: var(--ckqa-shadow-accent-glow);
}

.sb-brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  transition:
    max-width var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    opacity var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

.sb-brand-name {
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sb-brand-tagline {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Toggle 按钮：展开态嵌 brand row 右端 */
.sb-toggle {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  display: grid;
  place-items: center;
  background: var(--ckqa-surface-toggle-rest);
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 8px;
  box-shadow: var(--ckqa-shadow-sidebar-toggle-rest);
  color: var(--ckqa-text-muted);
  cursor: pointer;
}

.sb-toggle:hover {
  background: var(--ckqa-surface-toggle-hover);
  color: var(--ckqa-accent-strong);
  border-color: var(--ckqa-border-accent-soft);
  box-shadow: var(--ckqa-shadow-sidebar-toggle-hover);
}

/* ————— Body（可滚动 nav 列表） ————— */
.sb-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: visible;   /* 展开态 rail 会溢出左边；不能裁 */
  padding: 8px 0;
}

.sb-list {
  position: relative;    /* rail absolute 相对于此定位 */
  overflow: visible;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* ————— Section title ————— */
.sb-section-title {
  margin: 14px 10px 4px;
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
  font-weight: var(--ckqa-fw-semibold);
  transition:
    max-height var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    margin var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    opacity var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

/* ————— Nav item ————— */
.sb-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 9px;
  color: var(--ckqa-text-muted);
  text-decoration: none;
  font-size: var(--ckqa-text-base-size);
  line-height: var(--ckqa-text-base-line);
  transition:
    background var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}

.sb-item:hover {
  color: var(--ckqa-text);
  background: var(--ckqa-surface-muted);
}

.sb-item:hover .sb-icon {
  color: var(--ckqa-accent-strong);
}

.sb-item.is-active {
  color: var(--ckqa-accent-strong);
  background: var(--ckqa-accent-soft);
  font-weight: var(--ckqa-fw-medium);
}

.sb-item.is-active .sb-icon {
  color: var(--ckqa-accent-strong);
}

.sb-item:focus-visible {
  outline: none;
  box-shadow: var(--ckqa-focus-ring);
}

.sb-icon {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  color: var(--ckqa-text-muted);
  font-size: 18px;
  transition: color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}

.sb-label {
  flex: 1 1 auto;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition:
    max-width var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    opacity var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

.sb-count {
  flex: 0 0 auto;
  padding: 1px 7px;
  background: var(--ckqa-surface-muted);
  border-radius: 10px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  transition: opacity var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

/* ————— Active rail ————— */
.sb-rail {
  position: absolute;
  left: -12px;
  border-radius: 3px;
  background: linear-gradient(180deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  box-shadow: var(--ckqa-shadow-sidebar-rail-glow);
  transition:
    top var(--ckqa-duration-base) var(--ckqa-ease-spring),
    height var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    left var(--ckqa-duration-glass) var(--ckqa-ease-glass);
  pointer-events: none;
}

/* ————— Status card ————— */
.sb-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 12px;
  background: var(--ckqa-surface-glass-strong);
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 12px;
  box-shadow: var(--ckqa-shadow-sidebar-status);
}

.sb-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--ckqa-avatar-fallback-gradient);
  color: var(--ckqa-text-inverse);
  font-size: var(--ckqa-text-sm-size);
  font-weight: var(--ckqa-fw-semibold);
}

.sb-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  transition:
    max-width var(--ckqa-duration-glass) var(--ckqa-ease-glass),
    opacity var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

.sb-id-name {
  font-size: var(--ckqa-text-sm-size);
  line-height: var(--ckqa-text-sm-line);
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sb-id-role {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ————— 折叠态 ————— */
.sidebar.is-collapsed {
  width: 64px;
  padding: 14px 8px;
}

.sidebar.is-collapsed .sb-brand {
  justify-content: center;
  border-bottom: none;
  padding-bottom: 8px;
}

.sidebar.is-collapsed .sb-brand-text {
  max-width: 0;
  opacity: 0;
  flex: 0 0 0;
}

.sidebar.is-collapsed .sb-section-title {
  max-height: 0;
  margin: 0;
  overflow: hidden;
  opacity: 0;
}

.sidebar.is-collapsed .sb-label,
.sidebar.is-collapsed .sb-count {
  max-width: 0;
  opacity: 0;
}

.sidebar.is-collapsed .sb-item {
  width: 48px;
  height: 40px;
  padding: 0;
  margin: 0 auto 3px;
  justify-content: center;
  border-radius: 11px;
}

.sidebar.is-collapsed .sb-item:hover {
  background: var(--ckqa-surface-collapsed-hover);
  transform: scale(1.07);
  box-shadow: var(--ckqa-shadow-sidebar-item-collapsed-hover);
}

.sidebar.is-collapsed .sb-item:hover .sb-icon {
  color: var(--ckqa-accent-strong);
  transform: scale(1.05);
}

.sidebar.is-collapsed .sb-item:active {
  transform: scale(0.92);
}

.sidebar.is-collapsed .sb-item.is-active {
  animation: breathe-glow 4500ms ease-in-out 500ms infinite;
}

.sidebar.is-collapsed .sb-rail {
  left: -8px;
}

.sidebar.is-collapsed .sb-status {
  justify-content: center;
  padding: 8px;
  margin-top: 8px;
}

.sidebar.is-collapsed .sb-identity {
  max-width: 0;
  opacity: 0;
  flex: 0 0 0;
}

/* 折叠态 toggle：脱出 brand row，悬浮在 sidebar 右边缘 */
.sidebar.is-collapsed .sb-toggle {
  position: absolute;
  /* 28px 高的 toggle 中心对齐到 brand mark 中心 y=37
     top = 37 - 14 = 23（mark 顶=14+6=20，mark 中心=20+17=37） */
  top: 23px;
  right: -14px;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--ckqa-surface-toggle-collapsed);
  box-shadow: var(--ckqa-shadow-sidebar-toggle-collapsed);
  z-index: 20;
}

.sidebar.is-collapsed .sb-toggle svg {
  transform: rotate(180deg);
  transition: transform var(--ckqa-duration-base) var(--ckqa-ease-spring);
}

.sidebar.is-collapsed .sb-toggle:hover svg {
  transform: rotate(180deg) scale(1.15);
}

@keyframes breathe-glow {
  0%, 100% {
    box-shadow: var(--ckqa-shadow-sidebar-active-breathe-from);
  }

  50% {
    box-shadow: var(--ckqa-shadow-sidebar-active-breathe-to);
  }
}
</style>
