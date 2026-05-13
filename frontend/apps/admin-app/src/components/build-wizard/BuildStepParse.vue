<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
})

const PARSE_TASK_STATUS_LABELS = {
  done: '解析完成',
  running: '解析中',
  processing: '解析中',
  pending: '待解析',
  failed: '解析失败',
  ready: '可执行',
  blocked: '未满足条件',
}

function resolveParseTaskStatusLabel(row = {}) {
  return PARSE_TASK_STATUS_LABELS[row.status] ?? row.displayStatus ?? '状态未知'
}

function resolveProgressStatus(row = {}) {
  if (row.status === 'failed') return 'exception'
  if (row.status === 'done') return 'success'
  return undefined
}

function resolveProgressPercent(row = {}) {
  const value = Number(row.percent)
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
}

function isProgressIndeterminate(row = {}) {
  // running 阶段且没有真实百分比时（默认 50% 占位），显示 indeterminate 动效
  return ['running', 'processing'].includes(row.status) && Number(row.percent) === 50
}
</script>

<template>
  <section class="build-step-panel">
    <Transition name="slide-down">
      <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ operationFeedback.title }}</strong>
          <StatusBadge :status="operationFeedback.status" />
        </div>
        <p>{{ operationFeedback.message }}</p>
        <small>{{ operationFeedback.detail }}</small>
      </div>
    </Transition>
    <ol class="build-task-list" aria-live="polite">
      <li v-for="row in blocks.parseTasks?.items" :key="row.id" class="build-task-row">
        <div>
          <strong>{{ row.title }}</strong>
          <small>{{ row.detail }}</small>
        </div>
        <el-progress
          :percentage="resolveProgressPercent(row)"
          :status="resolveProgressStatus(row)"
          :indeterminate="isProgressIndeterminate(row)"
          :duration="3"
        />
        <StatusBadge :status="row.status" :label="resolveParseTaskStatusLabel(row)" />
      </li>
    </ol>
    <p v-if="blocks.parseTasks?.state === 'empty'">请先选择需要构建的课程资料。</p>
  </section>
</template>
