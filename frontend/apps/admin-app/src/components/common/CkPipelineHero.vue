<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import {
  PIPELINE_STAGES,
  resolveStageMetric,
  isStageActive,
  buildPipelineNavTarget,
} from './pipeline-hero-model.js'

const props = defineProps({
  summary: { type: Object, default: null },
  scopeParams: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
})

const router = useRouter()

const cards = computed(() =>
  PIPELINE_STAGES.map((stage) => ({
    stage,
    metric: resolveStageMetric(stage, props.summary),
    active: isStageActive(stage, props.summary),
  })),
)

function jump(stage) {
  if (props.loading) return
  const target = buildPipelineNavTarget(stage, props.scopeParams)
  router.push(target)
}
</script>

<template>
  <section class="ck-pipeline-hero" aria-label="生产流水线概览">
    <div
      v-for="card in cards"
      :key="card.stage.key"
      class="ck-pipeline-hero-card"
      :class="{ 'is-active': card.active, 'is-loading': loading }"
      role="button"
      tabindex="0"
      @click="jump(card.stage)"
      @keyup.enter="jump(card.stage)"
    >
      <header class="ck-pipeline-hero-card-head">
        <span class="ck-pipeline-hero-card-title">{{ card.stage.title }}</span>
        <span v-if="card.active" class="ck-pipeline-hero-card-pulse" aria-hidden="true" />
      </header>
      <strong class="ck-pipeline-hero-card-primary">{{ card.metric.primary }}</strong>
      <span v-if="card.metric.secondary" class="ck-pipeline-hero-card-secondary">
        {{ card.metric.secondary }}
      </span>
      <small class="ck-pipeline-hero-card-hint">{{ card.stage.hint }}</small>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ck-pipeline-hero {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
}
.ck-pipeline-hero-card {
  position: relative;
  padding: var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-lg);
  cursor: pointer;
  transition: border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard),
              box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard);
  display: flex; flex-direction: column; gap: var(--ckqa-space-1);
}
.ck-pipeline-hero-card:hover { border-color: var(--ckqa-border-strong); box-shadow: var(--ckqa-shadow-sm); }
.ck-pipeline-hero-card:focus-visible { outline: none; box-shadow: var(--ckqa-focus-ring); }
.ck-pipeline-hero-card.is-active {
  border-color: var(--ckqa-accent);
  box-shadow: 0 0 0 1px var(--ckqa-accent-soft) inset;
}
.ck-pipeline-hero-card.is-loading { pointer-events: none; opacity: 0.55; }
.ck-pipeline-hero-card-head {
  display: flex; justify-content: space-between; align-items: center;
}
.ck-pipeline-hero-card-title {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
}
.ck-pipeline-hero-card-pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--ckqa-accent);
  animation: pulse var(--ckqa-duration-slow) ease-in-out infinite alternate;
}
.ck-pipeline-hero-card-primary {
  font-size: var(--ckqa-text-xl-size);
  font-weight: var(--ckqa-fw-semibold);
  color: var(--ckqa-text);
}
.ck-pipeline-hero-card-secondary {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
}
.ck-pipeline-hero-card-hint {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
@media (max-width: 1080px) {
  .ck-pipeline-hero { grid-template-columns: repeat(2, 1fr); }
}
@keyframes pulse {
  from { transform: scale(1); opacity: 1; }
  to   { transform: scale(1.4); opacity: 0.5; }
}
</style>
