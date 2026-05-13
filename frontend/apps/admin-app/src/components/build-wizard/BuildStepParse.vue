<script setup>
import { computed } from 'vue'

import StatusBadge from '../common/StatusBadge.vue'

const props = defineProps({
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

// 实时进度已直观展示，进行中（running/confirming）不需要再叠一层提示框；
// 仅保留终止态（success/failed）的反馈以告知最终结论。
const visibleFeedback = computed(() => {
  const status = props.operationFeedback?.status
  if (!props.operationFeedback) return null
  if (status === 'success' || status === 'failed') return props.operationFeedback
  return null
})

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

function resolveProgressTone(row = {}) {
  // 颜色随进度从冷到暖渐变：冷蓝（< 30%）→ 青绿（< 60%）→ 暖橙（< 90%）→ 翠绿（done）
  if (row.status === 'failed') return 'danger'
  if (row.status === 'done') return 'success'
  const percent = resolveProgressPercent(row)
  if (percent >= 90) return 'hot'
  if (percent >= 60) return 'warm'
  if (percent >= 30) return 'cool'
  return 'cold'
}
</script>

<template>
  <section class="build-step-panel">
    <Transition name="slide-down">
      <div v-if="visibleFeedback" class="operation-feedback" :data-status="visibleFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ visibleFeedback.title }}</strong>
          <StatusBadge :status="visibleFeedback.status" />
        </div>
        <p>{{ visibleFeedback.message }}</p>
        <small v-if="visibleFeedback.detail">{{ visibleFeedback.detail }}</small>
      </div>
    </Transition>
    <ol class="build-task-list" aria-live="polite">
      <li
        v-for="row in blocks.parseTasks?.items"
        :key="row.id"
        class="build-task-row"
        :data-status="row.status"
      >
        <div class="build-task-row__title">
          <strong>{{ row.title }}</strong>
          <small>{{ row.detail }}</small>
        </div>
        <el-progress
          class="build-task-progress"
          :percentage="resolveProgressPercent(row)"
          :status="resolveProgressStatus(row)"
          :indeterminate="isProgressIndeterminate(row)"
          :duration="3"
          :stroke-width="10"
          :data-tone="resolveProgressTone(row)"
        />
        <StatusBadge :status="row.status" :label="resolveParseTaskStatusLabel(row)" />
      </li>
    </ol>
    <p v-if="blocks.parseTasks?.state === 'empty'">请先选择需要构建的课程资料。</p>
  </section>
</template>
