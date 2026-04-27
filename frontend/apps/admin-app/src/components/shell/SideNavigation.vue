<script setup>
import { computed } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

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
</script>

<template>
  <aside class="side-navigation" :class="{ compact }" aria-label="一级导航">
    <nav class="side-nav-scroll">
      <section
        v-for="group in groups"
        :key="group.key"
        class="nav-group"
        :class="`nav-group--${group.presentation}`"
      >
        <RouterLink
          v-if="group.presentation === 'single' && group.primaryItem"
          class="nav-link nav-link--single"
          :class="{ active: group.key === activeGroup && group.primaryItem.path === activePath }"
          :to="group.primaryItem.path"
        >
          <span class="nav-link-main">
            <span class="nav-link-title">{{ group.label }}</span>
            <span v-if="group.primaryItem.displayState === 'coming-soon'" class="nav-state">未开放</span>
            <ChevronRight v-else :size="14" aria-hidden="true" />
          </span>
          <small v-if="!compact">{{ group.hint }}</small>
        </RouterLink>

        <details v-else class="nav-folder" :open="group.key === activeGroup">
          <summary
            class="nav-folder-trigger"
            :class="{ active: group.key === activeGroup }"
          >
            <span class="nav-folder-copy">
              <span class="nav-folder-title">{{ group.label }}</span>
              <small v-if="!compact">{{ group.hint }}</small>
            </span>
            <ChevronRight class="nav-folder-icon" :size="15" aria-hidden="true" />
          </summary>

          <ul class="nav-items">
            <li v-for="item in group.items" :key="item.path">
              <RouterLink
                class="nav-link"
                :class="{ active: group.key === activeGroup && item.path === activePath }"
                :to="item.path"
              >
                <span class="nav-link-main">
                  <span class="nav-link-title">{{ item.title }}</span>
                  <span v-if="item.displayState === 'coming-soon'" class="nav-state">未开放</span>
                  <ChevronRight v-else :size="14" aria-hidden="true" />
                </span>
              </RouterLink>
            </li>
          </ul>
        </details>
      </section>
    </nav>
  </aside>
</template>
