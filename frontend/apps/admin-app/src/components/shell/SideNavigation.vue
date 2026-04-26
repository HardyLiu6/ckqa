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
      <section v-for="group in groups" :key="group.key" class="nav-group">
        <div class="nav-group-heading">
          <strong>{{ group.label }}</strong>
          <span v-if="!compact">{{ group.hint }}</span>
        </div>

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
              <small v-if="!compact">{{ item.name }}</small>
            </RouterLink>
          </li>
        </ul>
      </section>
    </nav>
  </aside>
</template>
