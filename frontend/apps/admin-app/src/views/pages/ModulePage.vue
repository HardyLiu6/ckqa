<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import { http } from '../../axios/index.js'
import { createQaSession, getQaTask, sendQaMessage } from '../../api/qa.js'
import {
  createIndexRun,
  getKnowledgeBase,
  listIndexRuns,
} from '../../api/knowledge-bases.js'
import { authStore } from '../../stores/auth.js'
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
  resolveApiErrorAction,
  resolveCleanMaterialQuery,
  resolveMaterialQuery,
  resolveOperationFeedback,
  selectLatestRunningOrSuccess,
} from './module-page-model.js'
import {
  isQaFailedState,
  isQaSuccessState,
  resolveQaPollingInterval,
  resolveQaStaleTimeout,
} from './qa-polling.js'

const DEFAULT_SMOKE_QUESTION = '请用一句话概括当前知识库的主要内容。'
const DEFAULT_SMOKE_MODE = 'basic'

const route = useRoute()
const router = useRouter()
const requestGuard = createStaleRequestGuard()

const baseConfig = computed(() => getModulePageConfig(route.name))
const liveState = ref(null)
const loadError = ref(null)
const requestState = ref('idle')
const activeStepKey = ref('')
const actionState = ref('idle')
const actionSnapshot = ref(null)
const activeOperationKey = ref('')
const blockLoadingKey = ref('')
const smokeQuestion = ref(DEFAULT_SMOKE_QUESTION)
const smokeQuestionEdited = ref(false)
const smokeResult = ref(null)
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
const operationFeedback = computed(() => resolveOperationFeedback(
  activeOperationKey.value,
  actionState.value,
  actionSnapshot.value,
))
const materialOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'material' ? operationFeedback.value : null
))
const indexOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'index' ? operationFeedback.value : null
))
const qaOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'qa' ? operationFeedback.value : null
))
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
  if (route.name !== 'knowledge-base-build') {
    smokeResult.value = null
    smokeQuestionEdited.value = false
    smokeQuestion.value = DEFAULT_SMOKE_QUESTION
  }
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

  if (loadError.value) {
    const action = resolveApiErrorAction(loadError.value, { route: routeSnapshot })
    if (action.type === 'redirect') {
      await router.replace(action.to)
      return
    }
    if (action.type === 'block') {
      loadError.value = {
        ...loadError.value,
        message: action.message,
      }
    }
  }

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
    operationKey: 'material-parse',
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
    operationKey: 'material-export',
    limits: LONG_TASK_LIMITS.export,
    ...createMaterialExportTaskOptions({
      materialId,
      payload,
      exportGraphRagRequest: exportGraphRag,
      listParseResultsRequest: listParseResults,
    }),
  })
}

function startLongTask({ operationKey, trigger, poll, isSuccess, isFailed, limits }) {
  cancelLongTask()
  activeOperationKey.value = operationKey
  actionSnapshot.value = null
  activeLongTaskController = createLongTaskController({
    trigger,
    poll,
    isSuccess: isSuccess ?? ((snapshot) => resolveLongTaskState(snapshot) === 'success'),
    isFailed: isFailed ?? ((snapshot) => resolveLongTaskState(snapshot) === 'failed'),
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: () => loadPage(),
    limits,
  })
  startActiveLongTask(activeLongTaskController)
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
  activeOperationKey.value = 'index-build'
  actionSnapshot.value = null
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
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: async () => {
      await getKnowledgeBase(kbId)
      await loadPage()
    },
    limits: LONG_TASK_LIMITS.index,
  })
  startActiveLongTask(activeLongTaskController)
}

function updateSmokeQuestion(event) {
  smokeQuestionEdited.value = true
  smokeQuestion.value = event.target.value
}

async function runQaSmoke() {
  const knowledgeBase = knowledgeBaseBlock.value?.item
  const activeIndexRunId = knowledgeBase?.activeIndexRunId ?? knowledgeBase?.activeIndexId
  const question = smokeQuestionEdited.value ? smokeQuestion.value.trim() : DEFAULT_SMOKE_QUESTION

  if (!activeIndexRunId || !question || actionRunning.value) {
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'qa-smoke'
  actionSnapshot.value = null
  smokeResult.value = null

  let sessionId = null
  let taskId = null
  const limits = {
    ...resolveQaPollingInterval({ mode: DEFAULT_SMOKE_MODE }, DEFAULT_SMOKE_MODE),
    ...resolveQaStaleTimeout({ mode: DEFAULT_SMOKE_MODE }, DEFAULT_SMOKE_MODE),
  }

  activeLongTaskController = createLongTaskController({
    trigger: async ({ signal }) => {
      const session = await createQaSession({
        userId: authStore.state.currentUser?.id ?? 1,
        courseId: knowledgeBase.courseId,
        knowledgeBaseId: knowledgeBase.id ?? route.params.kbId,
        sessionType: 'smoke',
        title: '知识库构建冒烟验证',
      }, { post: (url, payload) => http.post(url, payload, { signal }) })
      sessionId = session.id

      const submission = await sendQaMessage(sessionId, {
        mode: DEFAULT_SMOKE_MODE,
        content: question,
      }, { post: (url, payload) => http.post(url, payload, { signal }) })
      taskId = submission.taskId
      Object.assign(limits, {
        ...resolveQaPollingInterval(submission, DEFAULT_SMOKE_MODE),
        ...resolveQaStaleTimeout(submission, DEFAULT_SMOKE_MODE),
      })

      return {
        ...submission,
        sessionId,
      }
    },
    poll: async ({ signal }) => ({
      ...(await getQaTask(sessionId, taskId, { get: (url) => http.get(url, { signal }) })),
      sessionId,
    }),
    isSuccess: (snapshot) => isQaSuccessState(snapshot?.taskStatus ?? snapshot?.status),
    isFailed: (snapshot) => isQaFailedState(snapshot?.taskStatus ?? snapshot?.status),
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: (snapshot) => {
      smokeResult.value = {
        state: 'success',
        sessionId: snapshot.sessionId ?? sessionId,
        taskId: snapshot.taskId ?? taskId,
        content: snapshot.assistantMessage?.content ?? snapshot.answer ?? '问答任务已完成，后端未返回助手摘要。',
      }
    },
    onFailure: (snapshot) => {
      const error = createApiError(snapshot)
      smokeResult.value = {
        state: 'failed',
        sessionId,
        taskId,
        message: snapshot?.errorMessage ?? snapshot?.timeoutMessage ?? error.message,
      }
    },
    limits,
  })
  startActiveLongTask(activeLongTaskController)
}

function cancelLongTask() {
  if (activeLongTaskController) {
    activeLongTaskController.cancel()
    activeLongTaskController = null
  }

  actionState.value = 'idle'
  actionSnapshot.value = null
  activeOperationKey.value = ''
}

function startActiveLongTask(controller) {
  void controller.start().catch((error) => {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
  })
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
      <div
        v-if="indexOperationFeedback"
        class="operation-feedback"
        :data-status="indexOperationFeedback.status"
      >
        <div class="operation-feedback__heading">
          <strong>{{ indexOperationFeedback.title }}</strong>
          <StatusBadge :status="indexOperationFeedback.status" />
        </div>
        <p>{{ indexOperationFeedback.message }}</p>
        <small>{{ indexOperationFeedback.detail }}</small>
        <small v-if="indexOperationFeedback.meta">{{ indexOperationFeedback.meta }}</small>
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

    <article class="panel wide-panel">
      <div class="panel-heading">
        <h2>问答冒烟验证</h2>
        <button
          class="primary-button compact"
          type="button"
          :disabled="config.workflowSteps?.find((step) => step.key === 'smoke')?.status !== 'ready' || actionRunning || !smokeQuestion.trim()"
          @click="runQaSmoke"
        >
          发起冒烟验证
        </button>
      </div>
      <label class="field-label" for="smoke-question">验证问题</label>
      <input
        id="smoke-question"
        class="text-input"
        type="text"
        :value="smokeQuestion"
        :disabled="actionRunning"
        @input="updateSmokeQuestion"
      />
      <p v-if="config.workflowSteps?.find((step) => step.key === 'smoke')?.status !== 'ready'" class="inline-error">
        缺少激活索引，暂不可验证。
      </p>
      <div
        v-if="qaOperationFeedback"
        class="operation-feedback"
        :data-status="qaOperationFeedback.status"
      >
        <div class="operation-feedback__heading">
          <strong>{{ qaOperationFeedback.title }}</strong>
          <StatusBadge :status="qaOperationFeedback.status" />
        </div>
        <p>{{ qaOperationFeedback.message }}</p>
        <small>{{ qaOperationFeedback.detail }}</small>
        <small v-if="qaOperationFeedback.meta">{{ qaOperationFeedback.meta }}</small>
      </div>
      <div v-if="smokeResult?.state === 'success'" class="result-box">
        <strong>助手摘要</strong>
        <p>{{ smokeResult.content }}</p>
        <RouterLink class="text-link" :to="`/app/qa-sessions/${smokeResult.sessionId}`">
          查看问答会话
        </RouterLink>
      </div>
      <p v-else-if="smokeResult?.state === 'failed' && !qaOperationFeedback" class="inline-error">
        {{ smokeResult.message }}
      </p>
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
          <StatusBadge :status="item.meta" />
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
      <div
        v-if="materialOperationFeedback"
        class="operation-feedback"
        :data-status="materialOperationFeedback.status"
      >
        <div class="operation-feedback__heading">
          <strong>{{ materialOperationFeedback.title }}</strong>
          <StatusBadge :status="materialOperationFeedback.status" />
        </div>
        <p>{{ materialOperationFeedback.message }}</p>
        <small>{{ materialOperationFeedback.detail }}</small>
        <small v-if="materialOperationFeedback.meta">{{ materialOperationFeedback.meta }}</small>
      </div>
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
