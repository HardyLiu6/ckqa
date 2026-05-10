<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'
import {
  computeOverallPercent,
  normalizeStageInput,
  resolveStageTone,
} from './split-progress-model.js'

const props = defineProps({
  // 阶段列表；每项 { key, title, state, currentPct?, durationMs?, detail? }
  stages: { type: Array, default: () => [] },
  // 当前活跃阶段 key；为空时不强制提升 pending → running
  activeKey: { type: String, default: '' },
  // 当前活跃阶段完成度（0~100）
  currentPct: { type: Number, default: 0 },
  // 布局方向；向导右侧面板走垂直，资料详情解析进度也走垂直
  orientation: { type: String, default: 'vertical' },
  // 加权整体进度配置；传空则等权
  weights: { type: Object, default: null },
  // 是否展示顶部整体进度摘要
  showSummary: { type: Boolean, default: true },
})

const normalized = computed(() =>
  normalizeStageInput(props.stages, {
    activeKey: props.activeKey,
    currentPct: props.currentPct,
  }),
)
const overall = computed(() => computeOverallPercent(normalized.value, props.weights))

function toneOf(state) {
  return resolveStageTone(state)
}

function stageLabel(stage) {
  switch (stage.state) {
    case 'done':
      return '已完成'
    case 'running':
      return `进行中 ${stage.currentPct}%`
    case 'failed':
      return '失败'
    case 'skipped':
      return '已跳过'
    default:
      return '等待'
  }
}
</script>

<template>
  <div
    class="ck-split-progress"
    :data-orientation="orientation"
    role="list"
    aria-label="阶段进度"
    data-testid="split-progress"
  >
    <div v-if="showSummary" class="ck-split-progress-summary">
      <span class="ck-split-progress-label">整体进度</span>
      <strong class="ck-split-progress-value">{{ overall }}%</strong>
    </div>

    <ol class="ck-split-progress-stages">
      <li
        v-for="stage in normalized"
        :key="stage.key"
        class="ck-split-progress-stage"
        :data-state="stage.state"
        :data-testid="`stage-${stage.key}`"
        role="listitem"
      >
        <div
          class="ck-split-progress-stage-dot"
          :class="{ 'is-pulsing': toneOf(stage.state).dot }"
          aria-hidden="true"
        ></div>
        <div class="ck-split-progress-stage-body">
          <div class="ck-split-progress-stage-heading">
            <span class="ck-split-progress-stage-title">{{ stage.title }}</span>
            <CkStatusPill
              :tone="toneOf(stage.state).tone"
              :label="stageLabel(stage)"
              size="sm"
            />
          </div>
          <div v-if="stage.detail" class="ck-split-progress-stage-detail">
            {{ stage.detail }}
          </div>
          <div
            v-if="stage.state === 'running'"
            class="ck-split-progress-stage-bar"
            :aria-valuenow="stage.currentPct"
            aria-valuemin="0"
            aria-valuemax="100"
            role="progressbar"
          >
            <div
              class="ck-split-progress-stage-bar-fill"
              :style="{ width: `${stage.currentPct}%` }"
            ></div>
          </div>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped lang="scss">
.ck-split-progress {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.ck-split-progress-summary {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.ck-split-progress-value {
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-lg-size);
  font-weight: var(--ckqa-fw-semibold);
}
.ck-split-progress-stages {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  padding: 0;
  margin: 0;
  list-style: none;
}
.ck-split-progress-stage {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: var(--ckqa-space-3);
  align-items: start;
  position: relative;
}
.ck-split-progress-stage::before {
  /* 连接线：除最后一段外显示 */
  content: '';
  position: absolute;
  left: 6px;
  top: 14px;
  bottom: calc(-1 * var(--ckqa-space-3));
  width: 2px;
  background: var(--ckqa-border-soft);
}
.ck-split-progress-stage:last-child::before {
  display: none;
}
.ck-split-progress-stage-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-top: 4px;
  background: var(--ckqa-border);
  border: 2px solid var(--ckqa-surface);
  box-shadow: 0 0 0 1px var(--ckqa-border);
  position: relative;
  z-index: 1;
}
.ck-split-progress-stage[data-state='done'] .ck-split-progress-stage-dot {
  background: var(--ckqa-success);
  box-shadow: 0 0 0 1px var(--ckqa-success);
}
.ck-split-progress-stage[data-state='failed'] .ck-split-progress-stage-dot {
  background: var(--ckqa-danger);
  box-shadow: 0 0 0 1px var(--ckqa-danger);
}
.ck-split-progress-stage[data-state='running'] .ck-split-progress-stage-dot {
  background: var(--ckqa-running);
  box-shadow: 0 0 0 1px var(--ckqa-running);
}
.ck-split-progress-stage[data-state='skipped'] .ck-split-progress-stage-dot {
  background: var(--ckqa-blocked);
  box-shadow: 0 0 0 1px var(--ckqa-blocked);
}
.ck-split-progress-stage-dot.is-pulsing {
  animation: ck-split-pulse 1.8s ease-in-out infinite;
}
@keyframes ck-split-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 var(--ckqa-running-soft);
  }
  50% {
    box-shadow: 0 0 0 6px transparent;
  }
}
.ck-split-progress-stage-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ck-split-progress-stage-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--ckqa-space-2);
}
.ck-split-progress-stage-title {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ck-split-progress-stage-detail {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.ck-split-progress-stage-bar {
  margin-top: 4px;
  height: 4px;
  background: var(--ckqa-surface-muted);
  border-radius: 2px;
  overflow: hidden;
}
.ck-split-progress-stage-bar-fill {
  height: 100%;
  background: var(--ckqa-running);
  transition: width var(--ckqa-duration-base) var(--ckqa-ease-standard);
}
.ck-split-progress[data-orientation='horizontal'] .ck-split-progress-stages {
  flex-direction: row;
  flex-wrap: wrap;
}
.ck-split-progress[data-orientation='horizontal'] .ck-split-progress-stage::before {
  display: none;
}
@media (prefers-reduced-motion: reduce) {
  .ck-split-progress-stage-dot.is-pulsing {
    animation: none;
  }
  .ck-split-progress-stage-bar-fill {
    transition: none;
  }
}
</style>
