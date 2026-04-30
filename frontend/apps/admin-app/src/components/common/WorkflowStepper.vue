<script setup>
import { computed } from 'vue'
import { Play, ScrollText } from 'lucide-vue-next'

import StatusBadge from './StatusBadge.vue'
import {
  isWorkflowPrimaryActionDisabled,
  resolveActiveWorkflowStep,
} from '../../views/pages/module-content.js'

const props = defineProps({
  steps: { type: Array, required: true },
  activeKey: { type: String, default: '' },
})

const emit = defineEmits(['update:activeKey'])

const activeStep = computed(() => {
  return resolveActiveWorkflowStep(props.steps, props.activeKey)
})

const isBlocked = computed(() => isWorkflowPrimaryActionDisabled(activeStep.value))
const blockedReason = computed(() => activeStep.value?.detail || '前置条件未满足')

function selectStep(step) {
  emit('update:activeKey', step.key)
}
</script>

<template>
  <section class="workflow-stepper">
    <ol class="workflow-stepper__steps" aria-label="构建步骤">
      <li
        v-for="(step, index) in steps"
        :key="step.key"
        :class="{ active: step.key === activeStep?.key }"
      >
        <el-button
          class="workflow-stepper__step-button"
          native-type="button"
          @click="selectStep(step)"
        >
          <span class="workflow-stepper__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>
            <strong>{{ step.label }}</strong>
            <small>{{ step.detail }}</small>
          </span>
          <StatusBadge :status="step.status" />
        </el-button>
      </li>
    </ol>

    <article class="panel workflow-stepper__action" aria-live="polite">
      <p class="eyebrow">当前动作</p>
      <h2>{{ activeStep.label }}</h2>
      <p>{{ activeStep.detail }}</p>

      <div class="workflow-stepper__conditions">
        <strong>前置条件</strong>
        <ul>
          <li v-for="condition in activeStep.conditions" :key="condition">{{ condition }}</li>
        </ul>
      </div>

      <div class="workflow-actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          :disabled="isBlocked"
        >
          <Play class="button-icon" :size="16" aria-hidden="true" />
          {{ activeStep.actionLabel }}
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
        >
          <ScrollText class="button-icon" :size="16" aria-hidden="true" />
          {{ activeStep.logLabel }}
        </el-button>
      </div>

      <p v-if="isBlocked" class="workflow-stepper__blocked">阻塞：{{ blockedReason }}</p>
    </article>

    <aside class="panel workflow-stepper__status">
      <p class="eyebrow">任务状态</p>
      <div class="workflow-stepper__status-head">
        <strong>{{ activeStep.label }}</strong>
        <StatusBadge :status="activeStep.status" />
      </div>
      <p>{{ activeStep.logLabel }} · 当前状态来自页面数据与任务轮询。</p>
      <ol class="timeline-list">
        <li v-for="step in steps" :key="`${step.key}-log`">
          <StatusBadge :status="step.status" />
          <strong>{{ step.logLabel }}</strong>
          <small>{{ step.detail }}</small>
        </li>
      </ol>
    </aside>
  </section>
</template>
