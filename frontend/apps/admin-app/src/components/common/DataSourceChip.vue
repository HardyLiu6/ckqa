<script setup>
import { computed } from 'vue'

import { getDataSourceLabel } from './status-model.js'

const props = defineProps({
  source: { type: String, required: true },
  refreshedAt: { type: String, default: '' },
})

const label = computed(() => getDataSourceLabel(props.source))
const refreshLabel = computed(() => {
  if (props.source !== 'live' || !props.refreshedAt) return ''

  const date = new Date(props.refreshedAt)
  if (Number.isNaN(date.getTime())) return props.refreshedAt

  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
})
</script>

<template>
  <span class="data-source-chip" :data-source="source">
    <span>{{ label }}</span>
    <span v-if="refreshLabel" class="data-source-chip__time">· 已刷新 {{ refreshLabel }}</span>
  </span>
</template>
