<script setup>
import { computed, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, DatabaseZap } from 'lucide-vue-next'

import BuildStepMaterial from '../../../components/build-wizard/BuildStepMaterial.vue'
import BuildStepParse from '../../../components/build-wizard/BuildStepParse.vue'
import BuildStepExport from '../../../components/build-wizard/BuildStepExport.vue'
import BuildStepPrompt from '../../../components/build-wizard/BuildStepPrompt.vue'
import BuildStepIndex from '../../../components/build-wizard/BuildStepIndex.vue'
import BuildStepQaCheck from '../../../components/build-wizard/BuildStepQaCheck.vue'
import DataSourceChip from '../../../components/common/DataSourceChip.vue'
import StatusBadge from '../../../components/common/StatusBadge.vue'
import WorkflowStepper from '../../../components/common/WorkflowStepper.vue'
import RouteState from '../../status/RouteState.vue'
import { useBuildOperations } from '../../../composables/useBuildOperations.js'
import { useBuildWizardForm } from '../../../composables/useBuildWizardForm.js'

const props = defineProps({
  buildRunId: {
    type: Number,
    default: null,
  },
  kb: {
    type: Object,
    default: null,
  },
  readonly: {
    type: Boolean,
    default: false,
  },
})

const route = useRoute()
const router = useRouter()

const operations = useBuildOperations({
  readonly: () => props.readonly,
  route,
  router,
})

const state = useBuildWizardForm({
  buildRunId: () => props.buildRunId,
  kb: () => props.kb,
  readonly: () => props.readonly,
  operations,
  route,
  router,
  stepComponents: {
    material: BuildStepMaterial,
    parse: BuildStepParse,
    export: BuildStepExport,
    prompt: BuildStepPrompt,
    index: BuildStepIndex,
    qa_check: BuildStepQaCheck,
  },
})

const heroTitle = computed(() => route.meta?.title || '构建向导')
const heroEyebrow = computed(() => state.config.value.eyebrow || 'Build Wizard')
const heroSource = computed(() => state.config.value.source || state.config.value.dataSource || 'live')
const secondaryActionLabel = computed(() => (
  state.config.value.secondaryAction?.label
    ?? state.config.value.secondaryAction
    ?? '查看最近任务'
))

onBeforeUnmount(() => {
  operations.cancelLongTask()
})
</script>

<template>
  <article class="build-wizard-form" data-testid="build-wizard-form">
    <section class="module-hero">
      <div>
        <p v-if="heroEyebrow" class="eyebrow">{{ heroEyebrow }}</p>
        <div class="module-title-row">
          <h2>{{ heroTitle }}</h2>
          <DataSourceChip :source="heroSource" :refreshed-at="state.config.value.refreshedAt" />
        </div>
        <p>{{ state.config.value.summary }}</p>
      </div>

      <div class="button-row">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
        >
          <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
          {{ secondaryActionLabel }}
        </el-button>
      </div>
    </section>

    <WorkflowStepper
      :active-key="state.activeStepKey.value"
      :steps="state.config.value.workflowSteps ?? []"
      @update:active-key="state.updateActiveStep"
    />

    <section class="build-step-stage" :data-step="state.activeStep.value?.key">
      <header class="build-step-stage__header">
        <el-button
          v-if="state.navigation.value && !state.navigation.value.disabled"
          class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"
          native-type="button"
          :aria-label="state.navigation.value.previousLabel"
          @click="state.goPreviousStep"
        >
          <ChevronLeft class="button-icon" :size="18" aria-hidden="true" />
        </el-button>
        <div>
          <p class="eyebrow">STEP {{ state.stepIndexLabel.value }}</p>
          <h2>{{ state.activeStep.value?.label }}</h2>
          <p>{{ state.activeStep.value?.detail }}</p>
        </div>
        <StatusBadge
          v-if="state.activeStep.value?.status"
          :status="state.activeStep.value.status"
          :label="state.activeStep.value.displayStatus || state.activeStep.value.status"
        />
      </header>

      <div class="build-summary-strip">
        <span
          v-for="chip in state.summaryChips.value"
          :key="chip.label"
          class="build-summary-chip"
          :data-tone="chip.tone"
        >
          <strong>{{ chip.label }}</strong>
          <span>{{ chip.value }}</span>
        </span>
      </div>

      <div class="build-step-stage__body">
        <component
          :is="state.activeStepComponent.value"
          :blocks="state.config.value.blocks"
          :step="state.activeStep.value"
          :action-running="operations.actionRunning.value"
          :operation-feedback="state.activeOperationFeedback.value"
          :smoke-question="operations.smokeQuestion.value"
          :smoke-result="operations.smokeResult.value"
          @select-materials="state.updateMaterialSelection"
          @update-smoke-question="operations.updateSmokeQuestion"
        />
      </div>

      <footer class="build-step-stage__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          :disabled="state.primaryAction.value.disabled || operations.actionRunning.value"
          @click="state.handlePrimaryAction"
        >
          <component
            :is="state.primaryActionIcon.value"
            class="button-icon"
            :size="16"
            aria-hidden="true"
          />
          {{ state.primaryAction.value.label }}
        </el-button>
        <p v-if="state.primaryAction.value.disabledReason" class="inline-error">
          {{ state.primaryAction.value.disabledReason }}
        </p>
      </footer>
    </section>

    <RouteState
      v-if="state.loadError.value"
      variant="error"
      :title="state.loadError.value.message"
    />
  </article>
</template>

<style scoped lang="scss">
.build-step-stage {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface);
  box-shadow: var(--ckqa-shadow-sm);
}

.build-step-stage__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
}

.build-step-stage__header h2,
.build-step-stage__header p {
  margin: 0;
}

.build-step-stage__header p:not(.eyebrow) {
  margin-top: 6px;
  color: var(--ckqa-text-muted);
}

.build-step-stage__back.el-button {
  width: 36px;
  min-width: 36px;
  padding: 8px;
}

.build-summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.build-summary-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  color: var(--ckqa-text-muted);
  background: var(--ckqa-surface-muted);
}

.build-summary-chip strong {
  color: var(--ckqa-text);
}

.build-summary-chip[data-tone='ok'] {
  border-color: color-mix(in srgb, var(--ckqa-info) 24%, transparent);
  color: var(--ckqa-info);
  background: color-mix(in srgb, var(--ckqa-info) 8%, transparent);
}

.build-summary-chip[data-tone='warn'] {
  border-color: color-mix(in srgb, var(--ckqa-warning) 28%, transparent);
  color: var(--ckqa-warning-strong);
  background: color-mix(in srgb, var(--ckqa-warning) 9%, transparent);
}

.build-summary-chip[data-tone='info'] {
  border-color: color-mix(in srgb, var(--ckqa-blue) 24%, transparent);
  color: var(--ckqa-blue-strong);
  background: color-mix(in srgb, var(--ckqa-blue) 8%, transparent);
}

.build-step-stage__body,
.build-step-panel {
  display: grid;
  gap: 14px;
}

.build-step-stage__actions {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--ckqa-border);
  background: linear-gradient(180deg, color-mix(in srgb, var(--ckqa-surface) 86%, transparent), var(--ckqa-surface) 34%);
}

.build-step-stage__actions .el-button {
  min-height: 40px;
  white-space: normal;
}

.inline-error {
  margin-bottom: 14px;
  color: var(--ckqa-danger);
  font-weight: 800;
}
</style>
