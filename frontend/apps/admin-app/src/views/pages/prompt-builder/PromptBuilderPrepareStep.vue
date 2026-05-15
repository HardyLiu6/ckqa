<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AnnotationSampleList from './AnnotationSampleList.vue'
import AnnotationWorkArea from './AnnotationWorkArea.vue'
import { MOCK_AUDIT_SAMPLES, MOCK_TASK_SUMMARY } from './mocks/index.js'

const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 }
const samples = ref(
  MOCK_AUDIT_SAMPLES
    .map((s) => JSON.parse(JSON.stringify(s)))
    .sort((a, b) => (PRIORITY_ORDER[a.auditPriority] ?? 9) - (PRIORITY_ORDER[b.auditPriority] ?? 9))
)
const taskSummary = MOCK_TASK_SUMMARY
const tasksExpanded = ref(false)

const initialActiveId = samples.value.find((s) => s.status === 'in_progress')?.id
  ?? samples.value[0]?.id
  ?? ''
const activeSampleId = ref(initialActiveId)

const activeSample = computed(() =>
  samples.value.find((s) => s.id === activeSampleId.value) ?? null
)

function handleSelectSample(id) {
  activeSampleId.value = id
}

function handleAcceptEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  sample.goldEntities.push({ ...picked, source: 'accepted' })
  if (sample.status === 'not_started') sample.status = 'in_progress'
  ElMessage.success('已采纳')
}

function handleRejectEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedEntities = sample.aiSuggestedEntities.filter((e) => e.id !== entityId)
}

function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
}

function handleAcceptRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedRelations.splice(idx, 1)
  sample.goldRelations.push({ ...picked, source: 'accepted' })
  ElMessage.success('已采纳')
}

function handleRejectRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedRelations = sample.aiSuggestedRelations.filter((r) => r.id !== relationId)
}

function handleDeleteRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.goldRelations = sample.goldRelations.filter((r) => r.id !== relationId)
}

function handleFinishSample(sampleId) {
  const sample = samples.value.find((s) => s.id === sampleId)
  if (!sample) return
  if (sample.goldEntities.length === 0) {
    ElMessage.warning('至少标注 1 个实体后才能完成；如确实无可抽取实体，请点"跳过"')
    return
  }
  sample.status = 'done'
  const nextSample = samples.value.find((s) => s.status === 'not_started')
  if (nextSample) {
    activeSampleId.value = nextSample.id
    ElMessage.success('已完成')
  } else {
    ElMessage.success('已完成 · 所有样本已处理完毕，可前往下一步')
  }
}

function handleSkipSample(sampleId) {
  const sample = samples.value.find((s) => s.id === sampleId)
  if (!sample) return
  sample.status = 'skipped'
  const nextSample = samples.value.find((s) => s.status === 'not_started')
  if (nextSample) {
    activeSampleId.value = nextSample.id
    ElMessage.info('已跳过')
  } else {
    ElMessage.success('已跳过 · 所有样本已处理完毕，可前往下一步')
  }
}

function sortSuggestionsByConfidence() {
  if (!activeSample.value) return
  activeSample.value.aiSuggestedEntities.sort(
    (a, b) => (b.confidence ?? 0) - (a.confidence ?? 0)
  )
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-prepare">
    <header class="prompt-builder-step__header">
      <h3>构建准备材料</h3>
      <p>Phase 1b mock：所有数据都是假的，体验交互形态。</p>
    </header>

    <!-- 02.1 / 02.2 任务折叠条 -->
    <article class="prepare-task-summary" :class="{ 'is-expanded': tasksExpanded }">
      <header @click="tasksExpanded = !tasksExpanded">
        <strong>已完成的脚本任务</strong>
        <span class="ann-text-muted">02.1 调优样本集 · 02.2 校准集采样</span>
        <span class="prepare-task-summary__toggle">{{ tasksExpanded ? '收起 ▴' : '展开 ▾' }}</span>
      </header>
      <div v-if="tasksExpanded" class="prepare-task-summary__body">
        <div>
          <span>02.1 调优样本集</span>
          <strong>{{ taskSummary.samplesBuilt.count }} 条 · {{ taskSummary.samplesBuilt.types }} 类型 · 用时 {{ taskSummary.samplesBuilt.durationSec }} 秒</strong>
        </div>
        <div>
          <span>02.2 校准集采样</span>
          <strong>分层抽样 {{ taskSummary.auditSampled.total }} 条 · high {{ taskSummary.auditSampled.high }} / medium {{ taskSummary.auditSampled.medium }} / low {{ taskSummary.auditSampled.low }} · 用时 {{ taskSummary.auditSampled.durationSec }} 秒</strong>
        </div>
      </div>
    </article>

    <!-- 02.3 标注 IDE -->
    <div class="annotation-board">
      <AnnotationSampleList
        :samples="samples"
        :active-sample-id="activeSampleId"
        @select-sample="handleSelectSample"
      />
      <AnnotationWorkArea
        :sample="activeSample"
        @finish-sample="handleFinishSample"
        @skip-sample="handleSkipSample"
        @accept-entity="handleAcceptEntity"
        @reject-entity="handleRejectEntity"
        @delete-entity="handleDeleteEntity"
        @accept-relation="handleAcceptRelation"
        @reject-relation="handleRejectRelation"
        @delete-relation="handleDeleteRelation"
        @sort-suggestions-by-confidence="sortSuggestionsByConfidence"
      />
    </div>
  </section>
</template>
