<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'

import {
  resolveCardStatus,
  formatMetaEntries,
  normalizeMetaVariant,
} from './resource-card-model.js'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  status: { type: String, default: '' },
  statusLabel: { type: String, default: '' },
  meta: { type: Array, default: () => [] },
  to: { type: [String, Object], default: null },
  cover: { type: String, default: '' },
  titleClamp: { type: Number, default: 1 },
  statusFloating: { type: Boolean, default: false },
  metaVariant: { type: String, default: 'inline' },
})

const resolvedStatus = computed(() => resolveCardStatus(props.status, props.statusLabel))
const metaEntries = computed(() => formatMetaEntries(props.meta))
const resolvedMetaVariant = computed(() => normalizeMetaVariant(props.metaVariant))
const titleClampValue = computed(() => Math.max(1, Number(props.titleClamp) || 1))
const titleMultiline = computed(() => titleClampValue.value > 1)
</script>

<template>
  <article class="ck-resource-card ck-glass-card" data-testid="resource-card">
    <RouterLink v-if="to" :to="to" class="ck-resource-card-link">
      <figure v-if="cover || $slots.cover" class="ck-resource-card-cover">
        <slot name="cover">
          <img v-if="cover" :src="cover" :alt="title" loading="lazy" />
        </slot>
        <CkStatusPill
          v-if="statusFloating && resolvedStatus.label"
          class="ck-resource-card-status-floating"
          data-testid="resource-card-status-floating"
          :tone="resolvedStatus.tone"
          :label="resolvedStatus.label"
          size="sm"
        />
      </figure>
      <div class="ck-resource-card-body">
        <header class="ck-resource-card-header">
          <h3
            class="ck-resource-card-title"
            :class="{ 'ck-resource-card-title-multiline': titleMultiline }"
            :style="titleMultiline ? { '--ck-card-title-clamp': titleClampValue } : null"
            :data-clamp="titleClampValue"
          >{{ title }}</h3>
          <CkStatusPill
            v-if="!statusFloating && resolvedStatus.label"
            :tone="resolvedStatus.tone"
            :label="resolvedStatus.label"
            size="sm"
          />
        </header>
        <p v-if="description" class="ck-resource-card-description">{{ description }}</p>
        <ul
          v-if="metaEntries.length"
          class="ck-resource-card-meta"
          :class="`ck-resource-card-meta-${resolvedMetaVariant}`"
          :data-meta-variant="resolvedMetaVariant"
        >
          <li v-for="entry in metaEntries" :key="entry.label">
            <template v-if="resolvedMetaVariant === 'emphasis'">
              <strong>{{ entry.value }}</strong>
              <span>{{ entry.label }}</span>
            </template>
            <template v-else>
              <span>{{ entry.label }}</span>
              <strong>{{ entry.value }}</strong>
            </template>
          </li>
        </ul>
      </div>
    </RouterLink>
    <div v-if="$slots.actions" class="ck-resource-card-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<style scoped lang="scss">
.ck-resource-card {
  position: relative;
  overflow: hidden;
  transition:
    box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    transform var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-resource-card:hover {
  box-shadow: var(--ckqa-shadow-card-hover);
  transform: translateY(-2px);
  border-color: var(--ckqa-border-strong);
}
.ck-resource-card-link {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  color: inherit;
  text-decoration: none;
}
.ck-resource-card-cover {
  position: relative;
  margin: 0;
  aspect-ratio: 16 / 9;
  background: var(--ckqa-surface-muted);
  overflow: hidden;
}
.ck-resource-card-cover :slotted(img),
.ck-resource-card-cover :slotted(svg),
.ck-resource-card-cover img,
.ck-resource-card-cover svg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.ck-resource-card-status-floating {
  position: absolute;
  top: var(--ckqa-space-2);
  right: var(--ckqa-space-2);
  background: color-mix(in srgb, var(--ckqa-surface) 82%, transparent);
  backdrop-filter: blur(6px);
  border-radius: var(--ckqa-radius-pill, 999px);
  padding: 2px 8px;
}
.ck-resource-card-body {
  padding: var(--ckqa-space-3) var(--ckqa-space-4) var(--ckqa-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.ck-resource-card-header {
  display: flex;
  justify-content: space-between;
  gap: var(--ckqa-space-2);
  align-items: center;
}
.ck-resource-card-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-resource-card-title-multiline {
  white-space: normal;
  text-overflow: clip;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: var(--ck-card-title-clamp, 2);
  overflow: hidden;
}
.ck-resource-card-description {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.ck-resource-card-meta {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ckqa-space-2);
  font-size: var(--ckqa-text-xs-size);
}
.ck-resource-card-meta-inline li {
  display: flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
}
.ck-resource-card-meta-inline span {
  color: var(--ckqa-text-weak);
}
.ck-resource-card-meta-inline strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-resource-card-meta-emphasis li {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}
.ck-resource-card-meta-emphasis strong {
  font-size: var(--ckqa-text-xl-size);
  line-height: var(--ckqa-text-xl-line);
  font-weight: 700;
  color: var(--ckqa-text);
}
.ck-resource-card-meta-emphasis span {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
}
.ck-resource-card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}
</style>
