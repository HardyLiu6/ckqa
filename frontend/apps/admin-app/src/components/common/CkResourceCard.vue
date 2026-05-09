<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'

import {
  resolveCardStatus,
  formatMetaEntries,
} from './resource-card-model.js'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  status: { type: String, default: '' },
  statusLabel: { type: String, default: '' },
  meta: { type: Array, default: () => [] },
  to: { type: [String, Object], default: null },
  cover: { type: String, default: '' },
})

const resolvedStatus = computed(() => resolveCardStatus(props.status, props.statusLabel))
const metaEntries = computed(() => formatMetaEntries(props.meta))
</script>

<template>
  <article class="ck-resource-card ck-glass-card" data-test-id="resource-card">
    <RouterLink v-if="to" :to="to" class="ck-resource-card-link">
      <figure v-if="cover" class="ck-resource-card-cover">
        <img :src="cover" :alt="title" loading="lazy" />
      </figure>
      <div class="ck-resource-card-body">
        <header class="ck-resource-card-header">
          <h3 class="ck-resource-card-title">{{ title }}</h3>
          <CkStatusPill
            v-if="resolvedStatus.label"
            :tone="resolvedStatus.tone"
            :label="resolvedStatus.label"
            size="sm"
          />
        </header>
        <p v-if="description" class="ck-resource-card-description">{{ description }}</p>
        <ul v-if="metaEntries.length" class="ck-resource-card-meta">
          <li v-for="entry in metaEntries" :key="entry.label">
            <span>{{ entry.label }}</span>
            <strong>{{ entry.value }}</strong>
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
  transition: box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-resource-card:hover {
  box-shadow: var(--ckqa-shadow-card-hover);
}
.ck-resource-card-link {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  color: inherit;
  text-decoration: none;
}
.ck-resource-card-cover {
  margin: 0;
  aspect-ratio: 16 / 9;
  background: var(--ckqa-surface-muted);
  overflow: hidden;
}
.ck-resource-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
.ck-resource-card-meta li {
  display: flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
}
.ck-resource-card-meta span {
  color: var(--ckqa-text-weak);
}
.ck-resource-card-meta strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-resource-card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}
</style>
