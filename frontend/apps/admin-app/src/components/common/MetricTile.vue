<script setup>
import SkeletonBlock from './SkeletonBlock.vue'

defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  tone: { type: String, default: 'neutral' },
  loading: { type: Boolean, default: false },
})
</script>

<template>
  <article class="metric-tile" :data-tone="tone">
    <span class="metric-tile__label">{{ label }}</span>
    <SkeletonBlock v-if="loading" :rows="2" compact />
    <strong v-else class="metric-tile__value">{{ value }}</strong>
    <small class="metric-tile__hint">{{ hint }}</small>
  </article>
</template>
