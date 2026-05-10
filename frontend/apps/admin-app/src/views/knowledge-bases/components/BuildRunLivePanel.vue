<script setup>
import { computed } from 'vue'

import CkLogStream from '../../../components/common/CkLogStream.vue'
import CkSplitProgress from '../../../components/common/CkSplitProgress.vue'
import CkStatusPill from '../../../components/common/CkStatusPill.vue'

import BuildRunActions from './BuildRunActions.vue'
import BuildRunErrorBanner from './BuildRunErrorBanner.vue'

const props = defineProps({
  // BuildRun 当前状态：idle / running / processing / failed / done
  status: { type: String, default: 'idle' },
  // 阶段时间线（已经过 useBuildStageTimeline 合并）
  timeline: { type: Array, default: () => [] },
  activeKey: { type: String, default: '' },
  currentPct: { type: Number, default: 0 },
  overallPct: { type: Number, default: 0 },
  // CkLogStream 直接消费的 lines 数组
  logs: { type: Array, default: () => [] },
  // 失败原因（string），优先级高于阶段内 detail
  failureReason: { type: String, default: '' },
  // 失败阶段 key（用于 Banner 标题）
  failedStageKey: { type: String, default: '' },
  // 是否允许用户操作（只读运维时为 false）
  canAct: { type: Boolean, default: true },
  // 启动前的占位提示
  emptyHint: { type: String, default: '提交后将在这里实时显示构建过程' },
  // 数据模式：sse / polling / idle；展示用的角标
  mode: { type: String, default: 'idle' },
})

defineEmits(['retry', 'skip', 'cancel'])

const isIdle = computed(() => !props.timeline?.length || props.status === 'idle')

// 状态 → CkStatusPill 配色
const overallTone = computed(() => {
  switch (props.status) {
    case 'done':
    case 'success':
      return { tone: 'success', label: `构建完成 ${props.overallPct}%` }
    case 'failed':
      return { tone: 'danger', label: '构建失败' }
    case 'running':
    case 'processing':
    case 'indexing':
      return { tone: 'running', label: `进行中 ${props.overallPct}%` }
    default:
      return { tone: 'neutral', label: '尚未开始' }
  }
})

// 顶部 eyebrow 下的标题：当前阶段名
const currentStageTitle = computed(() => {
  if (isIdle.value) return '等待启动构建'
  const stage = props.timeline.find((item) => item.key === props.activeKey)
  return stage?.title ?? '构建进行中'
})

// 数据模式角标文案
const modeLabel = computed(() => {
  if (props.mode === 'sse') return '实时推送'
  if (props.mode === 'polling') return '定时刷新'
  return ''
})
</script>

<template>
  <aside
    class="build-run-live-panel ck-glass-card"
    data-testid="build-run-live-panel"
    :data-status="status"
    aria-label="构建实时面板"
  >
    <header class="build-run-live-panel__header">
      <div class="build-run-live-panel__heading">
        <span class="build-run-live-panel__eyebrow">实时构建</span>
        <h2 class="build-run-live-panel__title">{{ currentStageTitle }}</h2>
        <span v-if="modeLabel" class="build-run-live-panel__mode">{{ modeLabel }}</span>
      </div>
      <CkStatusPill :tone="overallTone.tone" :label="overallTone.label" />
    </header>

    <div v-if="isIdle" class="build-run-live-panel__placeholder" role="status" aria-live="polite">
      {{ emptyHint }}
    </div>

    <template v-else>
      <CkSplitProgress
        :stages="timeline"
        :active-key="activeKey"
        :current-pct="currentPct"
        :show-summary="true"
      />

      <BuildRunErrorBanner
        v-if="failureReason"
        :message="failureReason"
        :stage-key="failedStageKey"
        :can-retry="canAct"
        @retry="$emit('retry', $event)"
        @skip="$emit('skip', $event)"
      />

      <BuildRunActions
        v-if="canAct"
        :stage-key="activeKey"
        :status="status"
        :can-act="canAct"
        @retry="$emit('retry', $event)"
        @skip="$emit('skip', $event)"
        @cancel="$emit('cancel')"
      />

      <CkLogStream
        :lines="logs"
        :density-compact="true"
        :max-height="'280px'"
        empty-hint="等待日志输出"
      />
    </template>
  </aside>
</template>

<style scoped lang="scss">
.build-run-live-panel {
  padding: var(--ckqa-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.build-run-live-panel__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--ckqa-space-3);
}
.build-run-live-panel__heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  position: relative;
}
.build-run-live-panel__eyebrow {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
}
.build-run-live-panel__title {
  margin: 0;
  font-size: var(--ckqa-text-lg-size);
  font-weight: var(--ckqa-fw-semibold);
  color: var(--ckqa-text);
}
.build-run-live-panel__mode {
  margin-top: 4px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.build-run-live-panel__placeholder {
  padding: var(--ckqa-space-6);
  text-align: center;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
  border: 1px dashed var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface-muted);
}
</style>
