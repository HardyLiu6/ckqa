<script setup>
import { computed } from 'vue'

import StatusBadge from '../../components/common/StatusBadge.vue'
import { deriveTrackNodeState, PRODUCTION_STEPS } from './production-track-model.js'

const props = defineProps({
  nodes: { type: Array, required: true },
})

const normalizedNodes = computed(() => {
  return PRODUCTION_STEPS.map((step, index) => {
    const node = props.nodes.find((item) => item.key === step.key) || {}
    const state = deriveTrackNodeState(node.counts || {})

    return {
      ...step,
      ...node,
      label: node.label || step.label,
      index: index + 1,
      state,
    }
  })
})
</script>

<template>
  <section class="production-track ckqa-panel" aria-label="知识库生产链路">
    <div class="production-track__heading">
      <div>
        <p class="eyebrow">Production Track</p>
        <h2>生产链路</h2>
      </div>
    </div>
    <ol class="production-track__list">
      <li
        v-for="node in normalizedNodes"
        :key="node.key"
        class="production-track__node"
        :data-tone="node.state.tone"
      >
        <span class="production-track__index">{{ node.index }}</span>
        <div>
          <strong>{{ node.label }}</strong>
          <StatusBadge :status="node.state.tone" :label="node.state.label" />
        </div>
      </li>
    </ol>
  </section>
</template>
