<script setup>
import { computed } from 'vue'

import StatusBadge from './StatusBadge.vue'
import {
  resolveActiveWorkflowStep,
  resolveBuildProgress,
} from '../../views/pages/module-content.js'

const props = defineProps({
  steps: { type: Array, required: true },
  activeKey: { type: String, default: '' },
})

const emit = defineEmits(['update:activeKey'])

const activeStep = computed(() => {
  return resolveActiveWorkflowStep(props.steps, props.activeKey)
})
const progress = computed(() => resolveBuildProgress(props.steps))

function selectStep(step) {
  emit('update:activeKey', step.key)
}
</script>

<template>
  <section class="workflow-progress-rail" aria-label="构建进度">
    <header class="workflow-progress-rail__summary">
      <div>
        <p class="eyebrow">BUILD PROGRESS</p>
        <strong>{{ progress.summary }}</strong>
        <small>{{ progress.detail || '暂无阻塞项' }}</small>
      </div>
      <el-progress :percentage="progress.percent" :show-text="false" />
    </header>

    <ol class="workflow-progress-rail__steps" aria-label="构建步骤">
      <li
        v-for="(step, index) in steps"
        :key="step.key"
        :class="{ active: step.key === activeStep?.key }"
      >
        <el-button
          class="workflow-progress-rail__step"
          native-type="button"
          @click="selectStep(step)"
        >
          <span class="workflow-step-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="workflow-step-copy">
            <span class="workflow-step-label">{{ step.label }}</span>
            <small>{{ step.shortLabel || step.detail }}</small>
          </span>
          <StatusBadge :status="step.status" :label="step.displayStatus || step.status" />
        </el-button>
      </li>
    </ol>
  </section>
</template>
