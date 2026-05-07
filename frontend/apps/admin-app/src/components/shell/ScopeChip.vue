<script setup>
import { computed, ref } from 'vue'
import { useScopeStore, SCOPE_ALL, resolveScopeLabel } from '../../stores/scope.js'
import { authStore } from '../../stores/auth.js'

const scope = useScopeStore()
const open = ref(false)

const role = computed(() => authStore.state.currentUser?.role || 'guest')
const courses = computed(() => authStore.state.currentUser?.courseMemberships || [])

const label = computed(() => resolveScopeLabel({
  role: role.value,
  activeCourseId: scope.state.activeCourseId,
  courses: courses.value,
}))

const canSwitch = computed(() => role.value !== 'admin' && courses.value.length > 0)

function pickAll() {
  scope.setActiveCourseId(SCOPE_ALL)
  open.value = false
}
function pickCourse(id) {
  scope.setActiveCourseId(id)
  open.value = false
}
</script>

<template>
  <div class="scope-chip">
    <button
      class="scope-chip-trigger"
      type="button"
      :disabled="!canSwitch"
      :aria-expanded="open"
      :aria-haspopup="canSwitch ? 'menu' : undefined"
      @click="canSwitch && (open = !open)"
    >
      <span class="scope-chip-dot" aria-hidden="true" />
      <span class="scope-chip-label">{{ label }}</span>
      <span v-if="canSwitch" class="scope-chip-caret" aria-hidden="true">▾</span>
    </button>
    <ul v-if="open && canSwitch" class="scope-chip-menu" role="menu">
      <li role="menuitem" @click="pickAll">
        <span>全部我的课程</span>
        <span class="scope-chip-meta">{{ courses.length }}</span>
      </li>
      <li
        v-for="course in courses"
        :key="course.id"
        role="menuitem"
        :class="{ 'is-active': scope.state.activeCourseId === course.id }"
        @click="pickCourse(course.id)"
      >
        <span>{{ course.name }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.scope-chip { position: relative; }
.scope-chip-trigger {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-bg);
  border-radius: var(--ckqa-radius-full);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  cursor: pointer;
}
.scope-chip-trigger[disabled] { cursor: default; }
.scope-chip-trigger:not([disabled]):hover { background: var(--ckqa-surface-muted); color: var(--ckqa-text); }
.scope-chip-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--ckqa-success);
}
.scope-chip-caret { font-size: 9px; color: var(--ckqa-text-weak); }
.scope-chip-menu {
  position: absolute; top: 100%; left: 0; z-index: 30;
  margin: 6px 0 0; padding: 6px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
  list-style: none;
  min-width: 220px; max-height: 320px; overflow-y: auto;
}
.scope-chip-menu li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 10px;
  border-radius: var(--ckqa-radius-sm);
  cursor: pointer;
  font-size: var(--ckqa-text-sm-size);
}
.scope-chip-menu li:hover { background: var(--ckqa-surface-muted); }
.scope-chip-menu li.is-active { background: var(--ckqa-accent-soft); color: var(--ckqa-accent-strong); }
.scope-chip-meta { color: var(--ckqa-text-weak); font-size: var(--ckqa-text-xs-size); }
</style>
