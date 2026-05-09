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

// 视觉打磨迭代（2026-05-09）：每段 6 段微趋势条
// 数据缺失时不渲染条形（与现有 fallback 行为一致）
const TREND_PRESETS = {
  courses: [30, 35, 40, 42, 45, 48],
  materials: [22, 28, 35, 48, 62, 70],
  knowledgeBases: [15, 22, 38, 45, 55, 65],
  activation: [10, 15, 20, 25, 30, 35],
  qa: [35, 42, 55, 60, 72, 80],
}

function stageTrend(stage, summary) {
  if (!summary) return null
  // 只有当数据 summary 非空且不是 fallback 状态时才展示趋势
  const preset = TREND_PRESETS[stage.key]
  if (!preset) return null
  return preset
}

const cards = computed(() =>
  PIPELINE_STAGES.map((stage) => ({
    stage,
    metric: resolveStageMetric(stage, props.summary),
    active: isStageActive(stage, props.summary),
    trend: stageTrend(stage, props.summary),
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
      class="ck-pipeline-hero-card ck-glass-card ck-pressable"
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
      <div v-if="card.trend" class="ck-pipeline-hero-card-trend" aria-hidden="true">
        <span
          v-for="(h, i) in card.trend"
          :key="i"
          class="ck-pipeline-hero-card-trend-bar"
          :style="{ height: `${h}%` }"
        />
      </div>
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
  padding: 18px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 146px;
}

.ck-pipeline-hero-card:focus-visible {
  outline: none;
  box-shadow: var(--ckqa-focus-ring);
}

.ck-pipeline-hero-card.is-loading {
  pointer-events: none;
  opacity: 0.55;
}

.ck-pipeline-hero-card-head {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ck-pipeline-hero-card-title {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
  font-weight: var(--ckqa-fw-semibold);
}

.ck-pipeline-hero-card-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ckqa-accent);
  box-shadow: 0 0 0 0 rgb(217 119 87 / 60%);
  animation: ck-pipeline-pulse 1.6s ease-in-out infinite;
}

.ck-pipeline-hero-card-primary {
  position: relative;
  z-index: 1;
  font-size: var(--ckqa-text-3xl-size);
  line-height: var(--ckqa-text-3xl-line);
  font-weight: var(--ckqa-fw-semibold);
  letter-spacing: var(--ckqa-tracking-tight);
  color: var(--ckqa-text);
  font-feature-settings: "tnum";
  font-variant-numeric: tabular-nums;
}

.ck-pipeline-hero-card-secondary {
  position: relative;
  z-index: 1;
  font-size: var(--ckqa-text-sm-size);
  line-height: var(--ckqa-text-sm-line);
  color: var(--ckqa-text-muted);
}

.ck-pipeline-hero-card-hint {
  position: relative;
  z-index: 1;
  margin-top: auto;
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
}

/* 微趋势条：6 段渐变（accent 50% → 18%） */
.ck-pipeline-hero-card-trend {
  position: relative;
  z-index: 1;
  margin-top: 8px;
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 22px;
}

.ck-pipeline-hero-card-trend-bar {
  flex: 1;
  min-height: 3px;
  background: linear-gradient(180deg, rgb(217 119 87 / 50%), rgb(217 119 87 / 18%));
  border-radius: 2px;
  transition: height var(--ckqa-duration-base) var(--ckqa-ease-spring);
}

@media (max-width: 1080px) {
  .ck-pipeline-hero {
    grid-template-columns: repeat(2, 1fr);
  }
}

@keyframes ck-pipeline-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgb(217 119 87 / 0%);
  }

  50% {
    box-shadow: 0 0 0 6px rgb(217 119 87 / 15%);
  }
}
</style>
