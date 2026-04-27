<script setup>
import { computed } from 'vue'

import { getStatusTone } from './status-model.js'

const props = defineProps({
  status: { type: String, required: true },
  label: { type: String, default: '' },
})

const tone = computed(() => getStatusTone(props.status))
const text = computed(() => props.label || props.status)
</script>

<template>
  <span class="status-badge" :data-tone="tone" :aria-label="`状态：${text}`">
    {{ text }}
  </span>
</template>
