<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AnnotationSampleList from './AnnotationSampleList.vue'
import AnnotationWorkArea from './AnnotationWorkArea.vue'
import { MOCK_AUDIT_SAMPLES, MOCK_TASK_SUMMARY } from './mocks/index.js'

defineEmits(['back'])

const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 }
const samples = ref(
  MOCK_AUDIT_SAMPLES
    .map((s) => JSON.parse(JSON.stringify(s)))
    .sort((a, b) => (PRIORITY_ORDER[a.auditPriority] ?? 9) - (PRIORITY_ORDER[b.auditPriority] ?? 9))
)
const taskSummary = MOCK_TASK_SUMMARY
const tasksExpanded = ref(false)

// 全屏标注 IDE 模式
const ideOpen = ref(false)

const initialActiveId = samples.value.find((s) => s.status === 'in_progress')?.id
  ?? samples.value[0]?.id
  ?? ''
const activeSampleId = ref(initialActiveId)

const activeSample = computed(() =>
  samples.value.find((s) => s.id === activeSampleId.value) ?? null
)

const doneCount = computed(() => samples.value.filter((s) => s.status === 'done').length)
const totalCount = computed(() => samples.value.length)
const progressPercent = computed(() =>
  totalCount.value > 0 ? Math.round((doneCount.value / totalCount.value) * 100) : 0
)

function openIde() {
  ideOpen.value = true
  document.body.style.overflow = 'hidden'
}

function closeIde() {
  ideOpen.value = false
  document.body.style.overflow = ''
}

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
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>构建准备材料</h3>
        <p>生成调优样本与校准集，并完成人工标注。</p>
      </div>
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

    <!-- 02.3 校准集概览卡 + 进入标注入口 -->
    <div class="prepare-overview-card">
      <div class="prepare-overview-card__head">
        <div class="prepare-overview-card__title">
          <strong>校准集标注</strong>
          <span class="ann-pill ann-pill--accent">{{ doneCount }} / {{ totalCount }}</span>
        </div>
        <div class="prepare-overview-card__progress">
          <div :style="{ width: progressPercent + '%' }"></div>
        </div>
      </div>
      <div class="prepare-overview-card__body">
        <div class="prepare-overview-card__stats">
          <div>
            <span class="prepare-overview-card__stat-num">{{ totalCount }}</span>
            <span class="prepare-overview-card__stat-label">总样本</span>
          </div>
          <div>
            <span class="prepare-overview-card__stat-num">{{ doneCount }}</span>
            <span class="prepare-overview-card__stat-label">已完成</span>
          </div>
          <div>
            <span class="prepare-overview-card__stat-num">{{ totalCount - doneCount }}</span>
            <span class="prepare-overview-card__stat-label">待处理</span>
          </div>
        </div>
        <p class="prepare-overview-card__hint">
          在标注 IDE 中逐条审阅校准样本，确认实体与关系标注。完成后可进入下一步。
        </p>
        <el-button type="primary" size="large" class="prepare-overview-card__enter" @click="openIde">
          进入标注 IDE →
        </el-button>
      </div>
    </div>
  </section>

  <!-- 全屏标注 IDE 覆盖层 -->
  <Teleport to="body">
    <Transition name="ide-fade">
      <div v-if="ideOpen" class="annotation-ide-overlay">
        <!-- 顶部工具栏 -->
        <header class="annotation-ide-toolbar">
          <button class="annotation-ide-toolbar__back" @click="closeIde">
            ← 退出标注
          </button>
          <div class="annotation-ide-toolbar__center">
            <span class="annotation-ide-toolbar__title">校准集标注</span>
            <span class="ann-pill ann-pill--accent">{{ doneCount }} / {{ totalCount }}</span>
          </div>
          <div class="annotation-ide-toolbar__right">
            <span v-if="activeSample" class="annotation-ide-toolbar__current">
              当前：{{ activeSample.headingPath?.[activeSample.headingPath.length - 1] ?? '—' }}
            </span>
          </div>
        </header>

        <!-- 标注主体 -->
        <div class="annotation-ide-body">
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
      </div>
    </Transition>
  </Teleport>
</template>
