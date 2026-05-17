<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  BookOpen,
  Boxes,
  CircleHelp,
  DatabaseZap,
  Gauge,
  KeyRound,
  MessageSquareText,
  PanelLeftClose,
  PanelLeftOpen,
  ScrollText,
  Server,
  ShieldCheck,
  Sparkles,
  UserCog,
  Wrench,
} from 'lucide-vue-next'

import { findActiveNavigationPath } from './navigation-model.js'

const props = defineProps({
  groups: { type: Array, required: true },
  activeGroup: { type: String, required: true },
  currentPath: { type: String, required: true },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle-collapse'])

const router = useRouter()

const activePath = computed(() =>
  findActiveNavigationPath(props.groups, props.activeGroup, props.currentPath),
)

const groupIcons = {
  dashboard: Gauge,
  courses: BookOpen,
  knowledge: DatabaseZap,
  qa: MessageSquareText,
  users: ShieldCheck,
  system: Server,
}

function resolveGroupIcon(key) {
  return groupIcons[key] ?? Boxes
}

function resolveItemIcon(item) {
  if (item.name === 'health' || item.path?.includes('/health')) return Wrench
  if (item.name?.includes('audit') || item.title?.includes('审计')) return ScrollText
  if (item.name?.includes('permission') || item.title?.includes('权限')) return KeyRound
  if (item.name?.includes('user') || item.title?.includes('用户')) return UserCog
  if (item.name?.includes('qa') || item.title?.includes('问答')) return MessageSquareText
  if (item.name?.includes('knowledge') || item.title?.includes('知识库')) return Sparkles
  if (item.name?.includes('course') || item.title?.includes('课程')) return BookOpen
  if (item.name?.includes('material') || item.title?.includes('资料')) return Boxes
  return CircleHelp
}

/**
 * 单 item 入口（dashboard 等）：直接 nav 到 path
 */
function navigateTo(path) {
  if (!path) return
  router.push(path)
}

function isItemActive(path) {
  return activePath.value === path
}

function isGroupActive(group) {
  return props.activeGroup === group.key
}
</script>

<template>
  <aside
    class="side-navigation"
    :class="{ 'side-navigation--compact': compact }"
    aria-label="一级导航"
  >
    <nav class="side-nav-scroll">
      <template v-for="group in groups" :key="group.key">
        <!-- single group：dashboard 这类只有一个入口的组，渲染成单按钮 -->
        <button
          v-if="group.presentation === 'single' && group.primaryItem"
          type="button"
          class="side-nav-link side-nav-link--single"
          :class="{ 'is-active': isItemActive(group.primaryItem.path) }"
          :aria-current="isItemActive(group.primaryItem.path) ? 'page' : undefined"
          :title="compact ? `${group.label} · ${group.hint}` : ''"
          @click="navigateTo(group.primaryItem.path)"
        >
          <span class="side-nav-link__icon-wrap">
            <component :is="resolveGroupIcon(group.key)" :size="18" aria-hidden="true" />
          </span>
          <span v-if="!compact" class="side-nav-link__copy">
            <span class="side-nav-link__title">{{ group.label }}</span>
            <span class="side-nav-link__hint">{{ group.hint }}</span>
          </span>
          <span
            v-if="!compact && group.primaryItem.displayState === 'coming-soon'"
            class="side-nav-link__badge"
          >
            未开放
          </span>
        </button>

        <!-- 多入口分组：分块式卡片，永远展开 -->
        <section
          v-else
          class="side-nav-group"
          :class="{ 'is-active-group': isGroupActive(group) }"
          :aria-label="group.label"
        >
          <header class="side-nav-group__header" :title="compact ? `${group.label} · ${group.hint}` : ''">
            <span class="side-nav-group__icon">
              <component :is="resolveGroupIcon(group.key)" :size="18" aria-hidden="true" />
            </span>
            <span v-if="!compact" class="side-nav-group__copy">
              <span class="side-nav-group__title">{{ group.label }}</span>
              <span class="side-nav-group__hint">{{ group.hint }}</span>
            </span>
          </header>
          <ul class="side-nav-group__items" role="list">
            <li v-for="item in group.items" :key="item.path">
              <button
                type="button"
                class="side-nav-link side-nav-link--item"
                :class="{ 'is-active': isItemActive(item.path) }"
                :aria-current="isItemActive(item.path) ? 'page' : undefined"
                :title="compact ? item.title : ''"
                @click="navigateTo(item.path)"
              >
                <span class="side-nav-link__icon-wrap side-nav-link__icon-wrap--small">
                  <component :is="resolveItemIcon(item)" :size="15" aria-hidden="true" />
                </span>
                <span v-if="!compact" class="side-nav-link__title side-nav-link__title--item">{{ item.title }}</span>
                <span
                  v-if="!compact && item.displayState === 'coming-soon'"
                  class="side-nav-link__badge"
                >
                  未开放
                </span>
              </button>
            </li>
          </ul>
        </section>
      </template>
    </nav>

    <!-- 折叠按钮：底部贴边 -->
    <footer class="side-nav-footer">
      <button
        type="button"
        class="side-nav-collapse-btn"
        :aria-label="compact ? '展开侧边栏' : '折叠侧边栏'"
        :title="compact ? '展开侧边栏' : '折叠侧边栏'"
        @click="emit('toggle-collapse')"
      >
        <PanelLeftOpen v-if="compact" :size="18" aria-hidden="true" />
        <PanelLeftClose v-else :size="18" aria-hidden="true" />
        <span v-if="!compact" class="side-nav-collapse-btn__label">收起</span>
      </button>
    </footer>
  </aside>
</template>
