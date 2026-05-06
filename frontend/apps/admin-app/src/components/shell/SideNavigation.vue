<script setup>
import { computed } from 'vue'
import {
  BookOpen,
  Boxes,
  CircleHelp,
  DatabaseZap,
  Gauge,
  KeyRound,
  MessageSquareText,
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
</script>

<template>
  <aside class="side-navigation" :class="{ compact }" aria-label="一级导航">
    <nav class="side-nav-scroll">
      <el-menu
        class="side-menu"
        :default-active="activePath"
        :collapse="compact"
        router
      >
        <template v-for="group in groups" :key="group.key">
          <el-menu-item
            v-if="group.presentation === 'single' && group.primaryItem"
            class="side-menu-item side-menu-item--single"
            :index="group.primaryItem.path"
          >
            <component :is="resolveGroupIcon(group.key)" class="nav-icon" :size="18" aria-hidden="true" />
            <span class="nav-copy">
              <span class="nav-title">{{ group.label }}</span>
              <small v-if="!compact">{{ group.hint }}</small>
            </span>
            <span v-if="group.primaryItem.displayState === 'coming-soon' && !compact" class="nav-state">
              未开放
            </span>
          </el-menu-item>

          <el-sub-menu
            v-else
            class="side-sub-menu"
            :index="group.key"
          >
            <template #title>
              <component :is="resolveGroupIcon(group.key)" class="nav-icon" :size="18" aria-hidden="true" />
              <span class="nav-copy">
                <span class="nav-title">{{ group.label }}</span>
                <small v-if="!compact">{{ group.hint }}</small>
              </span>
            </template>

            <el-menu-item
              v-for="item in group.items"
              :key="item.path"
              class="side-menu-item"
              :index="item.path"
            >
              <component :is="resolveItemIcon(item)" class="nav-icon nav-icon--item" :size="16" aria-hidden="true" />
              <span class="nav-title">{{ item.title }}</span>
              <span v-if="item.displayState === 'coming-soon'" class="nav-state">未开放</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </nav>
  </aside>
</template>
