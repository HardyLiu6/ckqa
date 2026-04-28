<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import { http } from '../../axios/index.js'
import {
  createIndexRun,
  getKnowledgeBase,
  listIndexRuns,
} from '../../api/knowledge-bases.js'
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
  resolveCleanMaterialQuery,
  resolveMaterialQuery,
  selectLatestRunningOrSuccess,
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
    summary: liveState.value.summary ?? baseConfig.value.summary,
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
const knowledgeBaseBlock = computed(() => config.value.blocks?.knowledgeBase)
const indexRunsBlock = computed(() => config.value.blocks?.indexRuns)
const buildSelectionBlock = computed(() => config.value.blocks?.selection)
const knowledgeBaseRows = computed(() => {
  return route.name === 'knowledge-bases' ? (config.value.rows ?? []) : []
})

async function loadPage(query = route.query) {
  cancelLongTask()
  const requestId = requestGuard.next()
  const routeSnapshot = createRouteSnapshot(route, query)
  requestState.value = 'loading'
  loadError.value = null

  let result = null

  try {
    result = await loadModulePage(routeSnapshot, routeSnapshot.query)
  } catch (error) {
    result = {
      source: baseConfig.value.dataSource,
      requestState: 'error',
      error: createApiError(error),
    }
  }

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

  if (
    route.name === 'knowledge-base-build'
    && result.blocks?.selection?.shouldCleanMaterialQuery
    && route.query.materialId
  ) {
    await router.replace({ query: resolveCleanMaterialQuery(route.query) })
  }
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
  if (route.name === 'material-detail') {
    await runMaterialParse()
    return
  }

  if (route.name === 'knowledge-base-build') {
    await runKnowledgeBaseIndex()
  }
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

function startLongTask({ trigger, poll, isSuccess, isFailed, limits }) {
  cancelLongTask()
  activeLongTaskController = createLongTaskController({
    trigger,
    poll,
    isSuccess: isSuccess ?? ((snapshot) => resolveLongTaskState(snapshot) === 'success'),
    isFailed: isFailed ?? ((snapshot) => resolveLongTaskState(snapshot) === 'failed'),
    onState: (state, snapshot) => {
      actionState.value = state
      actionMessage.value = resolveActionMessage(state, snapshot)
    },
    onSuccess: () => loadPage(),
    limits,
  })
  void activeLongTaskController.start().catch(() => {})
}

async function selectBuildMaterial(materialId) {
  if (route.name !== 'knowledge-base-build') {
    return
  }

  await router.replace({ query: resolveMaterialQuery(route.query, materialId) })
}

async function runKnowledgeBaseIndex() {
  const kbId = route.params.kbId
  const indexStep = config.value.workflowSteps?.find((step) => step.key === 'index')

  if (indexStep?.status !== 'ready' || actionRunning.value) {
    return
  }

  cancelLongTask()
  activeLongTaskController = createLongTaskController({
    trigger: ({ signal }) => createIndexRun(kbId, { post: (url) => http.post(url, null, { signal }) }),
    poll: async ({ signal }) => selectLatestRunningOrSuccess(
      await listIndexRuns(kbId, { get: (url) => http.get(url, { signal }) }),
      resolveLongTaskState,
    ),
    isSuccess: (snapshot) => resolveLongTaskState(snapshot) === 'success',
    isFailed: (snapshot) => resolveLongTaskState(snapshot) === 'failed',
    onState: (state, snapshot) => {
      actionState.value = state
      actionMessage.value = resolveActionMessage(state, snapshot)
    },
    onSuccess: async () => {
      await getKnowledgeBase(kbId)
      await loadPage()
    },
    limits: LONG_TASK_LIMITS.index,
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

  <section v-if="route.name === 'knowledge-base-build'" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>本次构建主资料</h2>
        <span class="record-count">{{ buildSelectionBlock?.selectedMaterialId || '未选择' }}</span>
      </div>
      <ol class="timeline-list">
        <li v-for="item in materialsBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <button class="text-button" type="button" @click="selectBuildMaterial(item.id)">
            {{ item.title }}
          </button>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="materialsBlock?.state === 'empty'">当前课程暂无资料。</p>
      <p v-if="buildSelectionBlock?.error" class="inline-error">{{ buildSelectionBlock.error.message }}</p>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>索引运行</h2>
        <button
          class="primary-button compact"
          type="button"
          :disabled="config.workflowSteps?.find((step) => step.key === 'index')?.status !== 'ready' || actionRunning"
          @click="runKnowledgeBaseIndex"
        >
          开始构建索引
        </button>
      </div>
      <ol class="timeline-list">
        <li v-for="item in indexRunsBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="indexRunsBlock?.state === 'empty'">暂无索引运行。</p>
    </article>
  </section>

  <section v-else-if="route.name === 'knowledge-bases'" class="panel data-table-shell">
    <div class="panel-heading">
      <h2>{{ pageTitle }}</h2>
      <span class="record-count">{{ config.pagination?.total ?? knowledgeBaseRows.length }} 条</span>
    </div>
    <p v-if="loadError" class="inline-error">{{ loadError.message }}</p>
    <div v-if="loading" class="empty-state">正在加载列表。</div>
    <div v-else class="table-scroll">
      <table class="data-table" :aria-label="pageTitle">
        <thead>
          <tr>
            <th v-for="column in config.columns" :key="column" scope="col">{{ column }}</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody v-if="knowledgeBaseRows.length">
          <tr v-for="row in knowledgeBaseRows" :key="row.id">
            <td v-for="(cell, index) in row.cells" :key="`${row.id}-${index}`">
              <RouterLink v-if="index === 0" :to="row.to"><strong>{{ cell }}</strong></RouterLink>
              <StatusBadge v-else-if="index === 2" :status="cell" />
              <span v-else>{{ cell }}</span>
            </td>
            <td>
              <RouterLink class="text-link" :to="row.buildTo">构建</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!loading && !knowledgeBaseRows.length" class="empty-state">暂无知识库。</p>
    <div v-if="config.pagination" class="pagination-bar" aria-label="分页">
      <button
        class="secondary-button compact"
        type="button"
        :disabled="Number(config.pagination.page ?? 1) <= 1 || loading"
        @click="handlePageChange(Number(config.pagination.page ?? 1) - 1)"
      >
        上一页
      </button>
      <span>第 {{ config.pagination.page }} / {{ Math.max(config.pagination.pages, 1) }} 页</span>
      <button
        class="secondary-button compact"
        type="button"
        :disabled="Number(config.pagination.page ?? 1) >= Math.max(Number(config.pagination.pages ?? 1), 1) || loading"
        @click="handlePageChange(Number(config.pagination.page ?? 1) + 1)"
      >
        下一页
      </button>
    </div>
  </section>

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

  <section v-else-if="knowledgeBaseBlock || config.blocks?.indexRun" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>{{ knowledgeBaseBlock ? '知识库概览' : '索引运行概览' }}</h2>
        <StatusBadge :status="knowledgeBaseBlock?.item?.status ?? config.blocks?.indexRun?.item?.status" />
      </div>
      <div class="field-grid">
        <div
          v-for="field in (knowledgeBaseBlock?.facts ?? config.blocks?.indexRun?.facts)"
          :key="field.label"
          class="field-tile"
        >
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
      <RouterLink
        v-if="config.actions?.buildTo"
        class="primary-button compact"
        :to="config.actions.buildTo"
      >
        进入构建向导
      </RouterLink>
    </article>

    <article v-if="indexRunsBlock" class="panel">
      <div class="panel-heading">
        <h2>索引运行</h2>
      </div>
      <ol class="timeline-list">
        <li v-for="item in indexRunsBlock.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="indexRunsBlock.state === 'empty'">暂无索引运行。</p>
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
