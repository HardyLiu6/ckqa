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

import { findActiveNavigationPath, NAV_SECTIONS } from './navigation-model.js'

const props = defineProps({
  sections: { type: Array, default: () => [] },
  activeGroup: { type: String, default: '' },
  activePath: { type: String, default: '' },
  currentPath: { type: String, required: true },
  compact: { type: Boolean, default: false },
})

const sectionLabelMap = NAV_SECTIONS.reduce((acc, section) => {
  acc[section.key] = section.label
  return acc
}, {})

const computedActivePath = computed(() => {
  if (props.activePath) return props.activePath
  return findActiveNavigationPath(props.sections, props.currentPath)
})

const itemIconMap = {
  dashboard: Gauge,
  courses: BookOpen,
  materials: Boxes,
  'knowledge-bases': DatabaseZap,
  'qa-sessions': MessageSquareText,
  'retrieval-logs': ScrollText,
  'kb-validation': Sparkles,
  users: UserCog,
  health: Wrench,
  audit: ScrollText,
  permissions: KeyRound,
  roles: ShieldCheck,
  system: Server,
}

function resolveItemIcon(item) {
  return itemIconMap[item.key] || CircleHelp
}
</script>

<template>
  <aside class="side-navigation" :class="{ compact }" aria-label="一级导航">
    <nav class="side-nav-scroll">
      <el-menu
        class="side-menu"
        :default-active="computedActivePath"
        :collapse="compact"
        router
      >
        <template v-for="section in sections" :key="section.key">
          <div
            v-if="!compact && sectionLabelMap[section.key]"
            class="side-section-label"
          >
            {{ sectionLabelMap[section.key] }}
          </div>
          <el-menu-item
            v-for="item in section.items"
            :key="item.path"
            class="side-menu-item"
            :index="item.path"
          >
            <component :is="resolveItemIcon(item)" class="nav-icon" :size="18" aria-hidden="true" />
            <span class="nav-title">{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </nav>
  </aside>
</template>

<style scoped lang="scss">
.side-section-label {
  padding: var(--ckqa-space-3) var(--ckqa-space-4) 4px;
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  letter-spacing: 0.6px;
  text-transform: uppercase;
}
</style>
