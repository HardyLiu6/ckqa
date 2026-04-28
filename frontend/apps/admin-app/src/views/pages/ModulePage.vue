<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import {
  exportGraphRag,
  getMaterial,
  listParseResults,
  startParse,
} from '../../api/materials.js'
import DataSourceChip from '../../components/common/DataSourceChip.vue'
import DataTableShell from '../../components/common/DataTableShell.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import {
  LONG_TASK_LIMITS,
  createLongTaskController,
  resolveLongTaskState,
} from './long-task-state.js'
import { getModulePageConfig } from './module-content.js'
import {
  createMaterialExportTaskOptions,
  resolveMaterialExportPayload,
} from './material-lifecycle-actions.js'
import { loadCourseDetailBlock, loadModulePage } from './module-loaders.js'
import {
  buildPageQuery,
  createRouteSnapshot,
  createStaleRequestGuard,
} from './module-page-model.js'

const route = useRoute()
const router = useRouter()
const requestGuard = createStaleRequestGuard()

const baseConfig = computed(() => getModulePageConfig(route.name))
const liveState = ref(null)
const loadError = ref(null)
const requestState = ref('idle')
const activeStepKey = ref('')
const actionState = ref('idle')
const actionMessage = ref('')
const blockLoadingKey = ref('')
let activeLongTaskController = null
const config = computed(() => {
  if (!liveState.value) {
    return baseConfig.value
  }

  return {
    ...baseConfig.value,
    dataSource: liveState.value.source ?? baseConfig.value.dataSource,
    columns: liveState.value.columns ?? baseConfig.value.columns,
    rows: liveState.value.rows ?? baseConfig.value.rows,
    pagination: liveState.value.pagination ?? null,
    facts: liveState.value.facts ?? baseConfig.value.facts,
    workflowSteps: liveState.value.workflowSteps ?? baseConfig.value.workflowSteps,
    blocks: liveState.value.blocks ?? baseConfig.value.blocks,
    actions: liveState.value.actions ?? {},
    refreshedAt: liveState.value.refreshedAt,
    raw: liveState.value.raw,
  }
})
const loading = computed(() => requestState.value === 'loading')
const actionRunning = computed(() => ['running', 'confirming'].includes(actionState.value))
const pageTitle = computed(() => route.meta.title || config.value.eyebrow)
const primaryActionLabel = computed(() => config.value.primaryAction?.label ?? config.value.primaryAction)
const secondaryActionLabel = computed(() => config.value.secondaryAction?.label ?? config.value.secondaryAction)
const courseBlock = computed(() => config.value.blocks?.course)
const materialsBlock = computed(() => config.value.blocks?.materials)
const knowledgeBasesBlock = computed(() => config.value.blocks?.knowledgeBases)
const materialBlock = computed(() => config.value.blocks?.material)
const parseResultsBlock = computed(() => config.value.blocks?.parseResults)

async function loadPage(query = route.query) {
  cancelLongTask()
  const requestId = requestGuard.next()
  const routeSnapshot = createRouteSnapshot(route, query)
  requestState.value = 'loading'
  loadError.value = null

  const result = await loadModulePage(routeSnapshot, routeSnapshot.query)
  if (!requestGuard.isCurrent(requestId)) {
    return
  }

  if (!result) {
    liveState.value = null
    requestState.value = 'idle'
    return
  }

  liveState.value = result
  requestState.value = result.requestState
  loadError.value = result.error ? createApiError(result.error) : null
}

function handlePageChange(page) {
  router.replace({ query: buildPageQuery(route.query, page) })
}

async function retryCourseBlock(key) {
  if (route.name !== 'course-detail' || blockLoadingKey.value) {
    return
  }

  blockLoadingKey.value = key
  const routeSnapshot = createRouteSnapshot(route, route.query)

  try {
    const block = await loadCourseDetailBlock(routeSnapshot, key)
    liveState.value = {
      ...(liveState.value ?? {}),
      blocks: {
        ...(liveState.value?.blocks ?? {}),
        [key]: block,
      },
    }
  } finally {
    blockLoadingKey.value = ''
  }
}

async function handlePrimaryAction() {
  if (route.name !== 'material-detail') {
    return
  }

  await runMaterialParse()
}

async function handleSecondaryAction() {
  if (route.name !== 'material-detail') {
    return
  }

  await runMaterialExport()
}

async function runMaterialParse() {
  const materialId = route.params.materialId
  const actions = config.value.actions ?? {}

  if (!actions.canParse || actionRunning.value) {
    return
  }

  startLongTask({
    limits: LONG_TASK_LIMITS.parse,
    trigger: ({ signal }) => startParse(materialId, { signal }),
    poll: ({ signal }) => getMaterial(materialId, { signal }),
  })
}

async function runMaterialExport() {
  const materialId = route.params.materialId
  const actions = config.value.actions ?? {}

  if (!actions.canExport || actionRunning.value) {
    return
  }

  const payload = resolveMaterialExportPayload(
    actions,
    typeof window !== 'undefined' && typeof window.confirm === 'function'
      ? window.confirm.bind(window)
      : null,
  )

  if (!payload) {
    return
  }

  startLongTask({
    limits: LONG_TASK_LIMITS.export,
    ...createMaterialExportTaskOptions({
      materialId,
      payload,
      exportGraphRagRequest: exportGraphRag,
      listParseResultsRequest: listParseResults,
    }),
  })
}

function startLongTask({ trigger, poll, limits }) {
  cancelLongTask()
  activeLongTaskController = createLongTaskController({
    trigger,
    poll,
    isSuccess: (snapshot) => resolveLongTaskState(snapshot) === 'success',
    isFailed: (snapshot) => resolveLongTaskState(snapshot) === 'failed',
    onState: (state, snapshot) => {
      actionState.value = state
      actionMessage.value = resolveActionMessage(state, snapshot)
    },
    onSuccess: () => loadPage(),
    limits,
  })
  void activeLongTaskController.start().catch(() => {})
}

function cancelLongTask() {
  if (activeLongTaskController) {
    activeLongTaskController.cancel()
    activeLongTaskController = null
  }

  actionState.value = 'idle'
  actionMessage.value = ''
}

function resolveActionMessage(state, snapshot) {
  if (state === 'confirming') {
    return '请求可能仍在后端执行，正在确认最新状态。'
  }

  if (state === 'failed') {
    return snapshot?.message ?? '操作失败'
  }

  if (state === 'success') {
    return '操作完成'
  }

  return '任务已提交'
}

function renderFactValue(field) {
  return typeof field === 'string' ? '待确认' : field.value
}

function renderFactLabel(field) {
  return typeof field === 'string' ? field : field.label
}

watch(() => [route.name, route.params, route.query], () => loadPage(), { deep: true, immediate: true })
onBeforeUnmount(() => cancelLongTask())
</script>

<template>
  <section class="module-hero">
    <div>
      <p class="eyebrow">{{ config.eyebrow }}</p>
      <div class="module-title-row">
        <h2>{{ pageTitle }}</h2>
        <DataSourceChip :source="config.dataSource" :refreshed-at="config.refreshedAt" />
      </div>
      <p>{{ config.summary }}</p>
    </div>

    <div class="button-row">
      <button
        class="primary-button compact"
        type="button"
        :disabled="Boolean(config.primaryAction?.disabled) || (route.name === 'material-detail' && (!config.actions?.canParse || actionRunning))"
        :title="config.primaryAction?.title"
        @click="handlePrimaryAction"
      >
        {{ primaryActionLabel }}
      </button>
      <button
        class="secondary-button compact"
        type="button"
        :disabled="route.name === 'material-detail' && (!config.actions?.canExport || actionRunning)"
        @click="handleSecondaryAction"
      >
        {{ secondaryActionLabel }}
      </button>
    </div>
  </section>

  <section v-if="actionMessage" class="panel">
    <div class="panel-heading">
      <h2>任务状态</h2>
      <StatusBadge :status="actionState === 'confirming' ? 'running' : actionState" />
    </div>
    <p>{{ actionMessage }}</p>
  </section>

  <section v-if="loadError" class="panel">
    <div class="panel-heading">
      <h2>实时数据加载失败</h2>
      <button class="secondary-button compact" type="button" :disabled="loading" @click="loadPage()">
        重试
      </button>
    </div>
    <p class="inline-error">{{ loadError.message }}</p>
  </section>

  <WorkflowStepper
    v-if="config.variant === 'workflow'"
    v-model:active-key="activeStepKey"
    :steps="config.workflowSteps"
  />

  <DataTableShell
    v-else-if="config.variant === 'table'"
    :title="pageTitle"
    :columns="config.columns"
    :rows="config.rows"
    :filters="config.filters"
    :pagination="config.pagination"
    :loading="loading"
    :error="loadError"
    @page-change="handlePageChange"
  />

  <section v-else-if="courseBlock" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>课程概览</h2>
        <span class="record-count">{{ courseBlock.facts?.length ?? 0 }} 项</span>
      </div>
      <div class="field-grid">
        <div v-for="field in courseBlock.facts" :key="field.label" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>资料区块</h2>
        <button
          v-if="materialsBlock?.state === 'error'"
          class="secondary-button compact"
          type="button"
          :disabled="blockLoadingKey === 'materials'"
          @click="retryCourseBlock('materials')"
        >
          重试
        </button>
      </div>
      <p v-if="materialsBlock?.state === 'error'" class="inline-error">{{ materialsBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in materialsBlock?.items" :key="item.id">
          <StatusBadge :status="item.status ?? item.state" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.meta }} {{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="materialsBlock?.state === 'empty'">暂无课程资料。</p>
    </article>

    <article class="panel wide-panel">
      <div class="panel-heading">
        <h2>知识库区块</h2>
        <button
          v-if="knowledgeBasesBlock?.state === 'error'"
          class="secondary-button compact"
          type="button"
          :disabled="blockLoadingKey === 'knowledgeBases'"
          @click="retryCourseBlock('knowledgeBases')"
        >
          重试
        </button>
      </div>
      <p v-if="knowledgeBasesBlock?.state === 'error'" class="inline-error">{{ knowledgeBasesBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in knowledgeBasesBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <RouterLink class="text-link" :to="item.buildTo">构建</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="knowledgeBasesBlock?.state === 'empty'">暂无知识库。</p>
    </article>
  </section>

  <section v-else-if="materialBlock" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>资料概览</h2>
        <StatusBadge :status="materialBlock.item.parseStatus" />
      </div>
      <div class="field-grid">
        <div v-for="field in materialBlock.facts" :key="field.label" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
      <p v-if="config.actions?.parseHint">{{ config.actions.parseHint }}</p>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>解析结果</h2>
        <button v-if="parseResultsBlock?.state === 'error'" class="secondary-button compact" type="button" @click="loadPage()">
          重试
        </button>
      </div>
      <p v-if="parseResultsBlock?.state === 'error'" class="inline-error">{{ parseResultsBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in parseResultsBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <strong>{{ item.title }}</strong>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="parseResultsBlock?.state === 'empty'">暂无解析产物。</p>
    </article>
  </section>

  <section v-else class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>关键区域</h2>
        <span class="record-count">{{ config.facts?.length ?? 0 }} 项</span>
      </div>
      <div class="field-grid">
        <div v-for="field in config.facts" :key="renderFactLabel(field)" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>接入进度</h2>
      </div>
      <ol class="timeline-list">
        <li v-for="item in config.timeline" :key="item.label">
          <StatusBadge :status="item.status ?? item.state" />
          <strong>{{ item.label }}</strong>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
    </article>
  </section>
</template>
