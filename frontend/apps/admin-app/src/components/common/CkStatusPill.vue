<script setup>
import { computed } from 'vue'

import {
  resolvePillTone,
  resolvePillLabel,
  resolvePillStyleVars,
} from './status-pill-model.js'

const props = defineProps({
  status: { type: String, default: '' },
  tone: { type: String, default: '' },
  label: { type: String, default: '' },
  size: { type: String, default: 'md' },
})

const resolvedTone = computed(() => resolvePillTone(props.tone || props.status))
const resolvedLabel = computed(() => resolvePillLabel({ label: props.label, status: props.status }))
const styleVars = computed(() => resolvePillStyleVars(resolvedTone.value))
</script>

<template>
  <span
    class="ck-status-pill"
    :class="[`ck-status-pill--${size}`]"
    :style="styleVars"
    :data-tone="resolvedTone"
  >
    <span class="ck-status-pill-dot" aria-hidden="true" />
    <span class="ck-status-pill-label">{{ resolvedLabel }}</span>
  </span>
</template>

<style scoped lang="scss">
.ck-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: var(--ckqa-radius-full);
  background: var(--pill-bg);
  color: var(--pill-fg);
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
}
.ck-status-pill--sm { padding: 1px 7px; font-size: 10px; }
.ck-status-pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
</style>
