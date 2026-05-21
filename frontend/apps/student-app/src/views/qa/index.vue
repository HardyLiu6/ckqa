<!-- frontend/apps/student-app/src/views/qa/index.vue -->
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound,
  CircleCheck,
  Clock,
  Connection,
  Loading,
  Position,
  Reading,
  Refresh,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'

import { listCourses, listCourseKnowledgeBases } from '@/api/courses'
import {
  createQaSession,
  deleteQaMemoryItem,
  deleteQaFeedback,
  getQaMemoryPreference,
  getQaSession,
  listQaMemoryItems,
  getQaTask,
  listQaMessages,
  recommendQaMode,
  sendQaMessage,
  streamQaTaskEvents,
  submitQaFeedback,
  updateQaMemoryPreference,
  updateQaSession,
  warmupHybrid,
} from '@/api/qa'
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { useUserStore } from '@/stores/user'

import {
  QA_MODE_OPTIONS,
  SMART_QA_MODE,
  getModeOption,
  resolveQaMode,
  resolveQaModeRecommendation,
  resolveHybridWarmupText,
  resolveMemoryPolicyForMode,
  resolveModeWithHybridReadiness,
} from './qa-mode-model'
import QaMarkdownContent from './QaMarkdownContent.vue'
import {
  isTerminalTaskStatus,
  hasActiveIndexChanged,
  isArchivedReadOnlySession,
  isLegacyReadOnlySession,
  matchCourseForQuestion,
  learningMemoryTypeLabel,
  normalizeCourseList,
  normalizeKnowledgeBaseList,
  normalizeLearningMemory,
  normalizeMemoryPreference,
  normalizeQaFeedback,
  normalizeQaMessage,
  normalizeQaSession,
  resolveSessionLifecycleStatusText,
  resolveContextStatusText,
  resolveMemoryStatusText,
  resolvePollingDelaySeconds,
  selectReadyKnowledgeBase,
  upsertQaMessage,
} from './qa-session-model'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mainRef = ref(null)
const composerInputRef = ref(null)
const composerFocused = ref(false)
const plusMenuOpen = ref(false)
const modeMenuOpen = ref(false)
const courseSelectOpen = ref(false)
const kbSelectOpen = ref(false)

const courses = ref([])
const knowledgeBases = ref([])
const messages = ref([])
const selectedCourseId = ref('')
const selectedKnowledgeBaseId = ref('')
const selectedMode = ref(SMART_QA_MODE)
const allowHybridSmartBeta = ref(false)
const memoryPreference = ref(normalizeMemoryPreference(null))
const learningMemoryItems = ref([])
const input = ref(typeof route.query.topic === 'string' ? route.query.topic : '')
const activeSession = ref(null)
const pendingTask = ref(null)
const hybridWarmupStatus = ref('idle')
const hybridWarmupMessage = ref('')
const hybridWarmupCached = ref(false)
const loadingCourses = ref(false)
const loadingKnowledgeBases = ref(false)
const loadingMemory = ref(false)
const savingMemoryPreference = ref(false)
const deletingMemoryItemId = ref(null)
const restoringSession = ref(false)
const sending = ref(false)
const feedbackSubmittingMessageId = ref(null)
const errorMessage = ref('')
const statusMessage = ref('')
const memoryErrorMessage = ref('')
let pollTimer = null
let taskStreamController = null

const FEEDBACK_ACTIONS = [
  { key: 'helpful', label: '有用', rating: 'helpful', tags: [] },
  { key: 'unhelpful', label: '无用', rating: 'unhelpful', tags: [] },
  { key: 'source_irrelevant', label: '来源不相关', rating: 'needs_improvement', tags: ['source_irrelevant'] },
  { key: 'too_long', label: '太长', rating: 'needs_improvement', tags: ['too_long'] },
  { key: 'wants_example', label: '希望举例', rating: 'needs_improvement', tags: ['wants_example'] },
]

const selectedCourse = computed(() => (
  courses.value.find((course) => course.courseId === selectedCourseId.value) ?? null
))
const selectedKnowledgeBase = computed(() => (
  knowledgeBases.value.find((knowledgeBase) => String(knowledgeBase.id) === String(selectedKnowledgeBaseId.value)) ?? null
))
const readyKnowledgeBases = computed(() => (
  knowledgeBases.value.filter((knowledgeBase) => knowledgeBase.activeIndexRunId != null)
))
const modePreview = computed(() => resolveQaMode(input.value, selectedMode.value, qaModeResolveOptions()))
const activeModeOption = computed(() => getModeOption(modePreview.value.mode))
const hybridWarmupText = computed(() => resolveHybridWarmupText(hybridWarmupStatus.value, hybridWarmupCached.value))
const memoryScopeReady = computed(() => Boolean(selectedCourseId.value && selectedKnowledgeBaseId.value))
const memoryEnabled = computed(() => Boolean(memoryScopeReady.value && memoryPreference.value.enabled))
const memoryStatusText = computed(() => {
  if (!memoryScopeReady.value) {
    return '选择课程和知识库后可启用'
  }
  return memoryEnabled.value ? '已开启跨对话学习记忆' : '已关闭跨对话学习记忆'
})
const memorySendStatusText = computed(() => {
  if (modePreview.value.mode === 'local' && memoryEnabled.value) {
    return 'Local 问答会按问题动态使用学习记忆'
  }
  return resolveMemoryStatusText({
    mode: modePreview.value.mode,
    memoryApplied: false,
    memoryStrategy: 'off',
    memorySourceCount: learningMemoryItems.value.length,
    memorySizeEstimate: { chars: estimateLearningMemoryChars() },
  })
})
const isEmpty = computed(() => messages.value.length === 0 && !pendingTask.value)
const activeSessionReadOnlyMessage = computed(() => (
  resolveSessionLifecycleStatusText(activeSession.value)
  || (isLegacyReadOnlySession(activeSession.value)
    ? '该旧会话创建于索引版本固化前，请基于当前索引新建会话后继续提问'
    : '')
))
const activeIndexChanged = computed(() => hasActiveIndexChanged(activeSession.value, selectedKnowledgeBase.value))
const canSend = computed(() => Boolean(
  input.value.trim()
  && !sending.value
  && !pendingTask.value
  && !activeSessionReadOnlyMessage.value,
))
const selectedScopeLabel = computed(() => {
  if (selectedCourse.value && selectedKnowledgeBase.value) {
    return `${selectedCourse.value.name} / ${selectedKnowledgeBase.value.name}`
  }
  if (selectedCourse.value) {
    return `${selectedCourse.value.name} / 待选择知识库`
  }
  return '可先提问，系统会尝试识别课程'
})

onMounted(async () => {
  await loadCourses()
  await restoreSessionFromQuery()
})

onBeforeUnmount(() => {
  clearPollTimer()
  clearTaskStream()
})

async function loadCourses() {
  loadingCourses.value = true
  errorMessage.value = ''
  try {
    const payload = await listCourses({ page: 1, size: 100, status: 'active' })
    courses.value = normalizeCourseList(payload)
    const queryCourseId = typeof route.query.courseId === 'string' ? route.query.courseId : ''
    if (queryCourseId && courses.value.some((course) => course.courseId === queryCourseId)) {
      selectedCourseId.value = queryCourseId
      await loadKnowledgeBases(queryCourseId)
    }
  } catch (error) {
    errorMessage.value = error?.message || '课程列表加载失败'
  } finally {
    loadingCourses.value = false
  }
}

async function loadKnowledgeBases(courseId, options = {}) {
  if (!courseId) {
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    resetMemoryState()
    return []
  }

  loadingKnowledgeBases.value = true
  errorMessage.value = ''
  try {
    const payload = await listCourseKnowledgeBases(courseId)
    const nextKnowledgeBases = normalizeKnowledgeBaseList(payload)
    knowledgeBases.value = nextKnowledgeBases
    const selected = selectReadyKnowledgeBase(nextKnowledgeBases, selectedKnowledgeBaseId.value)
    selectedKnowledgeBaseId.value = selected.knowledgeBase?.id ? String(selected.knowledgeBase.id) : ''
    if (options.loadMemory !== false) {
      await loadMemoryState(courseId, selectedKnowledgeBaseId.value)
    }
    return nextKnowledgeBases
  } catch (error) {
    errorMessage.value = error?.message || '知识库列表加载失败'
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    resetMemoryState()
    return []
  } finally {
    loadingKnowledgeBases.value = false
  }
}

async function handleCourseChange(courseId) {
  resetConversation()
  resetMemoryState()
  await clearSessionQuery()
  selectedKnowledgeBaseId.value = ''
  await loadKnowledgeBases(courseId)
}

async function handleKnowledgeBaseChange() {
  resetConversation()
  await loadMemoryState(selectedCourseId.value, selectedKnowledgeBaseId.value)
  await clearSessionQuery()
}

function resetConversation() {
  clearPollTimer()
  clearTaskStream()
  activeSession.value = null
  pendingTask.value = null
  messages.value = []
  hybridWarmupStatus.value = 'idle'
  hybridWarmupMessage.value = ''
  hybridWarmupCached.value = false
}

function resetMemoryState() {
  memoryPreference.value = normalizeMemoryPreference({
    courseId: selectedCourseId.value,
    knowledgeBaseId: selectedKnowledgeBaseId.value || null,
    enabled: false,
  })
  learningMemoryItems.value = []
  memoryErrorMessage.value = ''
}

async function loadMemoryState(courseId = selectedCourseId.value, knowledgeBaseId = selectedKnowledgeBaseId.value) {
  if (!courseId || !knowledgeBaseId) {
    resetMemoryState()
    return
  }

  const requestCourseId = String(courseId)
  const requestKnowledgeBaseId = String(knowledgeBaseId)
  loadingMemory.value = true
  memoryErrorMessage.value = ''
  try {
    const [preferencePayload, itemsPayload] = await Promise.all([
      getQaMemoryPreference({ courseId: requestCourseId, knowledgeBaseId: requestKnowledgeBaseId }),
      listQaMemoryItems({ courseId: requestCourseId, knowledgeBaseId: requestKnowledgeBaseId }),
    ])
    if (
      requestCourseId !== String(selectedCourseId.value)
      || requestKnowledgeBaseId !== String(selectedKnowledgeBaseId.value)
    ) {
      return
    }
    memoryPreference.value = normalizeMemoryPreference({
      ...preferencePayload,
      courseId: requestCourseId,
      knowledgeBaseId: preferencePayload?.knowledgeBaseId ?? knowledgeBaseId,
    })
    const list = Array.isArray(itemsPayload) ? itemsPayload : itemsPayload?.items ?? itemsPayload?.records ?? []
    learningMemoryItems.value = list.map(normalizeLearningMemory).filter((item) => item.id)
  } catch (error) {
    memoryPreference.value = normalizeMemoryPreference({
      courseId: requestCourseId,
      knowledgeBaseId,
      enabled: false,
    })
    learningMemoryItems.value = []
    memoryErrorMessage.value = error?.message || '学习记忆状态加载失败，已按关闭处理'
  } finally {
    loadingMemory.value = false
  }
}

async function ensureMemoryStateForScope(courseId, knowledgeBaseId) {
  if (
    String(memoryPreference.value.courseId) === String(courseId)
    && String(memoryPreference.value.knowledgeBaseId) === String(knowledgeBaseId)
  ) {
    return
  }
  await loadMemoryState(courseId, knowledgeBaseId)
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value || pendingTask.value) {
    return
  }

  if (!userStore.user?.id) {
    errorMessage.value = '请先登录后再提问'
    return
  }
  if (activeSessionReadOnlyMessage.value) {
    errorMessage.value = activeSessionReadOnlyMessage.value
    return
  }

  sending.value = true
  errorMessage.value = ''

  try {
    if (!courses.value.length) {
      await loadCourses()
    }
    const course = await resolveCourse(text)
    const nextKnowledgeBases = knowledgeBases.value.length
      ? knowledgeBases.value
      : await loadKnowledgeBases(course.courseId)
    const knowledgeBaseResult = selectReadyKnowledgeBase(nextKnowledgeBases, selectedKnowledgeBaseId.value)
    if (knowledgeBaseResult.status !== 'ready') {
      throw new Error('当前课程还没有可用知识库索引，请先完成知识库构建')
    }

    const knowledgeBase = knowledgeBaseResult.knowledgeBase
    selectedCourseId.value = course.courseId
    selectedKnowledgeBaseId.value = String(knowledgeBase.id)
    await ensureMemoryStateForScope(course.courseId, knowledgeBase.id)

    let modeResolution = await resolveModeForSend(text, course, knowledgeBase)
    if (modeResolution.mode === 'hybrid_v0') {
      await ensureHybridWarmup(course, knowledgeBase)
      modeResolution = resolveModeWithHybridReadiness(modeResolution, {
        selectedMode: selectedMode.value,
        warmupStatus: hybridWarmupStatus.value,
      })
    }
    const session = await ensureSession(course, knowledgeBase, text)
    const submission = await sendQaMessage(session.id, {
      mode: modeResolution.mode,
      content: text,
      memoryPolicy: resolveMemoryPolicyForMode(modeResolution.mode, memoryEnabled.value),
      clientRoutingSnapshot: buildClientRoutingSnapshot(modeResolution),
    })

    messages.value = upsertQaMessage(messages.value, submission.userMessage)
    pendingTask.value = {
      ...submission,
      sessionId: session.id,
      mode: modeResolution.mode,
      routeReason: modeResolution.reason,
      queryText: text,
    }
    const confidenceHint = modeResolution.manualSwitchSuggested
      ? '推荐不够确定，可手动切换模式。'
      : ''
    const memoryStatus = resolveMemoryStatusText({
      ...submission,
      mode: modeResolution.mode,
    })
    statusMessage.value = modeResolution.fromSmart
      ? `智能推荐为 ${modeResolution.mode} 模式：${modeResolution.reason}。${resolveContextStatusText(submission)}。${memoryStatus}`
      : `已使用 ${modeResolution.mode} 模式提交问题。${resolveContextStatusText(submission)}。${memoryStatus}`
    statusMessage.value = `${statusMessage.value}${confidenceHint}`
    input.value = ''
    await scrollToBottom()
    startTaskStream(session.id, submission.taskId, submission)
  } catch (error) {
    errorMessage.value = error?.message || '问答请求失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    sending.value = false
  }
}

function qaModeResolveOptions() {
  return {
    allowHybridBeta: allowHybridSmartBeta.value,
    hasConversationContext: messages.value.length > 0 || Boolean(activeSession.value),
  }
}

function buildClientRoutingSnapshot(modeResolution = {}) {
  if (!modeResolution.fromSmart && !modeResolution.fromServer && !modeResolution.reviewPriority) {
    return undefined
  }
  return {
    selectedMode: selectedMode.value,
    recommendedMode: modeResolution.originalRecommendedMode || modeResolution.mode,
    fallbackMode: modeResolution.fallbackMode,
    confidence: modeResolution.confidence,
    confidenceBand: modeResolution.confidenceBand,
    reviewPriority: modeResolution.reviewPriority || 'normal',
    manualSwitchSuggested: Boolean(modeResolution.manualSwitchSuggested),
    reasons: modeResolution.routeReasons,
    routeScores: modeResolution.routeScores,
  }
}

async function resolveModeForSend(text, course, knowledgeBase) {
  const localResolution = resolveQaMode(text, selectedMode.value, qaModeResolveOptions())
  if (selectedMode.value !== SMART_QA_MODE) {
    return localResolution
  }
  try {
    const recommendation = await recommendQaMode({
      courseId: course.courseId,
      knowledgeBaseId: knowledgeBase.id,
      sessionId: activeSession.value?.id,
      question: text,
      betaHybridEnabled: allowHybridSmartBeta.value,
      hasConversationContext: messages.value.length > 0 || Boolean(activeSession.value),
    })
    return resolveQaModeRecommendation(localResolution, recommendation)
  } catch (error) {
    return {
      ...localResolution,
      fromServer: false,
      reason: `服务端智能推荐暂不可用，已使用本地规则：${localResolution.reason}`,
    }
  }
}

async function handleModeSelect(mode) {
  selectedMode.value = mode
  if (mode === 'hybrid_v0' && selectedCourse.value && selectedKnowledgeBase.value) {
    await ensureHybridWarmup(selectedCourse.value, selectedKnowledgeBase.value)
  }
}

async function handleHybridBetaToggle() {
  if (
    allowHybridSmartBeta.value
    && modePreview.value.mode === 'hybrid_v0'
    && selectedCourse.value
    && selectedKnowledgeBase.value
  ) {
    await ensureHybridWarmup(selectedCourse.value, selectedKnowledgeBase.value)
  }
}

async function handleMemoryToggle(nextEnabled) {
  if (!memoryScopeReady.value || savingMemoryPreference.value) {
    return
  }

  const previousPreference = memoryPreference.value
  const courseId = selectedCourseId.value
  const knowledgeBaseId = selectedKnowledgeBaseId.value
  savingMemoryPreference.value = true
  memoryErrorMessage.value = ''
  memoryPreference.value = normalizeMemoryPreference({
    ...previousPreference,
    courseId,
    knowledgeBaseId,
    enabled: nextEnabled,
  })
  try {
    const preference = await updateQaMemoryPreference({
      courseId,
      knowledgeBaseId,
      enabled: nextEnabled,
    })
    memoryPreference.value = normalizeMemoryPreference({
      ...preference,
      courseId,
      knowledgeBaseId: preference?.knowledgeBaseId ?? knowledgeBaseId,
    })
    ElMessage.success(nextEnabled ? '已开启跨对话学习记忆' : '已关闭跨对话学习记忆')
  } catch (error) {
    memoryPreference.value = previousPreference
    memoryErrorMessage.value = error?.message || '学习记忆设置保存失败'
    ElMessage.error(memoryErrorMessage.value)
  } finally {
    savingMemoryPreference.value = false
  }
}

async function handleDeleteMemoryItem(item) {
  if (!item?.id || deletingMemoryItemId.value) {
    return
  }
  deletingMemoryItemId.value = item.id
  memoryErrorMessage.value = ''
  try {
    await deleteQaMemoryItem(item.id)
    learningMemoryItems.value = learningMemoryItems.value.filter((memory) => memory.id !== item.id)
    ElMessage.success('学习记忆已删除')
  } catch (error) {
    memoryErrorMessage.value = error?.message || '学习记忆删除失败'
    ElMessage.error(memoryErrorMessage.value)
  } finally {
    deletingMemoryItemId.value = null
  }
}

async function ensureHybridWarmup(course, knowledgeBase) {
  if (!course?.courseId || !knowledgeBase?.id) {
    return
  }
  if (hybridWarmupStatus.value === 'ready' || hybridWarmupStatus.value === 'warming') {
    return
  }
  hybridWarmupStatus.value = 'warming'
  hybridWarmupCached.value = false
  hybridWarmupMessage.value = '正在预热本地混合检索索引'
  try {
    const result = await warmupHybrid({
      courseId: course.courseId,
      knowledgeBaseId: knowledgeBase.id,
    })
    hybridWarmupStatus.value = result?.ready ? 'ready' : 'not_ready'
    hybridWarmupCached.value = Boolean(result?.cached)
    hybridWarmupMessage.value = result?.message || (result?.ready ? '混合检索已就绪' : '混合检索准备未完成，可继续降级尝试')
  } catch (error) {
    hybridWarmupStatus.value = 'fallback'
    hybridWarmupCached.value = false
    hybridWarmupMessage.value = error?.message || '混合检索预热失败，将按懒加载降级尝试'
  }
}

async function resolveCourse(question) {
  if (selectedCourse.value) {
    return selectedCourse.value
  }

  const result = matchCourseForQuestion(question, courses.value)
  if (result.status !== 'matched') {
    throw new Error(result.status === 'ambiguous'
      ? '问题可匹配多个课程，请先手动选择课程'
      : '暂未识别到课程，请先选择课程后提问')
  }

  selectedCourseId.value = result.course.courseId
  statusMessage.value = `已根据问题识别课程：${result.course.name}`
  await loadKnowledgeBases(result.course.courseId)
  return result.course
}

async function ensureSession(course, knowledgeBase, firstQuestion) {
  if (
    activeSession.value
    && activeSession.value.courseId === course.courseId
    && String(activeSession.value.knowledgeBaseId) === String(knowledgeBase.id)
  ) {
    return activeSession.value
  }

  const createdSession = await createQaSession({
    userId: userStore.user.id,
    courseId: course.courseId,
    knowledgeBaseId: knowledgeBase.id,
    title: buildSessionTitle(firstQuestion),
  })
  activeSession.value = normalizeQaSession(createdSession)
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      courseId: course.courseId,
      sessionId: String(activeSession.value.id),
    },
  })
  return activeSession.value
}

async function restoreSessionFromQuery() {
  const sessionId = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  if (!sessionId) {
    return
  }

  restoringSession.value = true
  errorMessage.value = ''
  try {
    const session = normalizeQaSession(await getQaSession(sessionId))
    activeSession.value = session
    selectedCourseId.value = session.courseId
    if (session.courseId) {
      await loadKnowledgeBases(session.courseId, { loadMemory: false })
    }
    selectedKnowledgeBaseId.value = session.knowledgeBaseId != null ? String(session.knowledgeBaseId) : ''
    await loadMemoryState(session.courseId, selectedKnowledgeBaseId.value)
    const list = await listQaMessages(session.id)
    messages.value = list.map(normalizeQaMessage)
    statusMessage.value = activeSessionReadOnlyMessage.value || '已恢复历史会话，可以继续提问'
    await scrollToBottom()
  } catch (error) {
    errorMessage.value = error?.message || '历史会话恢复失败'
  } finally {
    restoringSession.value = false
  }
}

async function startNewIndexedSession() {
  resetConversation()
  await clearSessionQuery()
  statusMessage.value = '已切换为基于当前索引的新会话，发送下一条问题时会自动创建'
}

async function restoreActiveSession() {
  if (!activeSession.value?.id) {
    return
  }
  try {
    activeSession.value = normalizeQaSession(await updateQaSession(activeSession.value.id, { status: 'active' }))
    statusMessage.value = '会话已恢复，可以继续提问'
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error?.message || '会话恢复失败'
    ElMessage.error(errorMessage.value)
  }
}

async function clearSessionQuery() {
  if (!route.query.sessionId) {
    return
  }
  const { sessionId, ...restQuery } = route.query
  await router.replace({ path: route.path, query: restQuery })
}

function buildSessionTitle(question) {
  const normalized = question.trim()
  return normalized.length > 28 ? `${normalized.slice(0, 28)}...` : normalized
}

function scheduleTaskPoll(sessionId, taskId, delaySeconds) {
  clearPollTimer()
  pollTimer = window.setTimeout(() => {
    pollTask(sessionId, taskId)
  }, delaySeconds * 1000)
}

function startTaskStream(sessionId, taskId, submission) {
  clearTaskStream()
  if (typeof AbortController === 'undefined') {
    scheduleTaskPoll(sessionId, taskId, resolvePollingDelaySeconds(submission))
    return
  }

  const controller = new AbortController()
  taskStreamController = controller
  const state = {
    terminal: false,
    fallbackStarted: false,
    messageReceived: false,
  }

  const fallbackToPolling = (reason = '事件流暂不可用，已切换为轮询。') => {
    if (state.terminal || state.fallbackStarted || controller.signal.aborted) {
      return
    }
    state.fallbackStarted = true
    statusMessage.value = reason
    scheduleTaskPoll(sessionId, taskId, Math.min(2, resolvePollingDelaySeconds(pendingTask.value || submission)))
  }

  streamQaTaskEvents(sessionId, taskId, {
    open() {
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        streamText: '',
      }
    },
    status(payload) {
      if (!payload) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        ...payload,
        sessionId,
        streaming: true,
      }
      updateUserMessageTask(payload)
    },
    heartbeat() {
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        lastStreamHeartbeatAt: new Date().toISOString(),
      }
    },
    delta(payload) {
      const text = payload?.text ?? ''
      if (!text) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        streamText: `${pendingTask.value?.streamText ?? ''}${text}`,
      }
      scrollToBottom()
    },
    sources(payload) {
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        sources: Array.isArray(payload) ? payload : [],
      }
    },
    message(payload) {
      if (payload) {
        state.messageReceived = true
        messages.value = upsertQaMessage(messages.value, normalizeQaMessage(payload))
        pendingTask.value = {
          ...(pendingTask.value ?? submission),
          sessionId,
          streaming: true,
          streamText: '',
        }
      }
    },
    async done(payload) {
      state.terminal = true
      clearTaskStream()
      if (!state.messageReceived) {
        await refreshAssistantAfterEmptySuccess(sessionId, taskId)
      }
      if (memoryScopeReady.value) {
        await loadMemoryState(selectedCourseId.value, selectedKnowledgeBaseId.value)
      }
      statusMessage.value = `回答已生成。${resolveContextStatusText(pendingTask.value || payload)}。${resolveMemoryStatusText(pendingTask.value || payload)}`
      pendingTask.value = null
      await scrollToBottom()
    },
    error(payloadOrError) {
      const isNetworkError = payloadOrError instanceof Error
      if (isNetworkError) {
        fallbackToPolling('事件流连接中断，已切换为轮询。')
        return
      }
      state.terminal = true
      clearTaskStream()
      errorMessage.value = payloadOrError?.message || '问答任务执行失败'
      pendingTask.value = null
    },
    close() {
      fallbackToPolling()
    },
  }, {
    signal: controller.signal,
  }).catch(() => {
    fallbackToPolling('事件流连接失败，已切换为轮询。')
  })
}

async function pollTask(sessionId, taskId) {
  try {
    const detail = await getQaTask(sessionId, taskId)
    pendingTask.value = {
      ...(pendingTask.value ?? {}),
      ...detail,
      sessionId,
    }
    updateUserMessageTask(detail)

    if (!isTerminalTaskStatus(detail.taskStatus)) {
      scheduleTaskPoll(sessionId, taskId, resolvePollingDelaySeconds(detail))
      return
    }

    if (detail.taskStatus === 'success') {
      if (detail.assistantMessage) {
        messages.value = upsertQaMessage(messages.value, detail.assistantMessage)
      } else {
        await refreshAssistantAfterEmptySuccess(sessionId, taskId)
      }
      if (memoryScopeReady.value) {
        await loadMemoryState(selectedCourseId.value, selectedKnowledgeBaseId.value)
      }
      statusMessage.value = `回答已生成。${resolveContextStatusText(detail)}。${resolveMemoryStatusText(detail)}`
      pendingTask.value = null
      await scrollToBottom()
      return
    }

    const fallbackMessage = detail.taskStatus === 'stale'
      ? (detail.timeoutMessage || '任务心跳超时，请稍后重试')
      : (detail.errorMessage || '问答任务执行失败')
    errorMessage.value = fallbackMessage
    pendingTask.value = null
  } catch (error) {
    errorMessage.value = error?.message || '问答任务轮询失败'
    pendingTask.value = null
  }
}

async function refreshAssistantAfterEmptySuccess(sessionId, taskId) {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await sleep(700)
    const detail = await getQaTask(sessionId, taskId)
    pendingTask.value = {
      ...(pendingTask.value ?? {}),
      ...detail,
      sessionId,
    }
    if (detail.assistantMessage) {
      messages.value = upsertQaMessage(messages.value, detail.assistantMessage)
      return
    }
  }
  const list = await listQaMessages(sessionId)
  messages.value = list.map(normalizeQaMessage)
}

function sleep(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function updateUserMessageTask(detail) {
  if (!detail?.userMessageId) {
    return
  }
  messages.value = messages.value.map((message) => (
    message.id === detail.userMessageId
      ? {
          ...message,
          taskStatus: detail.taskStatus,
          progressStage: detail.progressStage,
        }
      : message
  ))
}

function isFeedbackActionActive(message, action) {
  const feedback = message?.feedback
  if (!feedback) {
    return false
  }
  if (action.tags.length > 0) {
    return action.tags.every((tag) => feedback.tags.includes(tag))
  }
  return feedback.rating === action.rating && feedback.tags.length === 0
}

async function handleFeedback(message, action) {
  if (!message?.id || feedbackSubmittingMessageId.value) {
    return
  }

  feedbackSubmittingMessageId.value = message.id
  try {
    if (isFeedbackActionActive(message, action)) {
      await deleteQaFeedback(message.id)
      updateMessageFeedback(message.id, null)
      ElMessage.success('已取消反馈')
      return
    }

    const feedback = await submitQaFeedback({
      messageId: message.id,
      rating: action.rating,
      tags: action.tags,
    })
    updateMessageFeedback(message.id, normalizeQaFeedback(feedback))
    ElMessage.success('反馈已记录')
  } catch (error) {
    ElMessage.error(error?.message || '反馈提交失败')
  } finally {
    feedbackSubmittingMessageId.value = null
  }
}

function updateMessageFeedback(messageId, feedback) {
  messages.value = messages.value.map((message) => (
    message.id === messageId ? { ...message, feedback } : message
  ))
}

function clearPollTimer() {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

function clearTaskStream() {
  if (taskStreamController) {
    taskStreamController.abort()
    taskStreamController = null
  }
}

async function refreshScope() {
  await loadCourses()
  if (selectedCourseId.value) {
    await loadKnowledgeBases(selectedCourseId.value)
  }
}

async function scrollToBottom() {
  await nextTick()
  mainRef.value?.scrollTo({ top: mainRef.value.scrollHeight, behavior: 'smooth' })
}

function autoResizeInput(event) {
  const el = event.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

function formatMessageTime(value) {
  if (!value) {
    return '刚刚'
  }
  const localDateTimeMatch = String(value).match(/^\d{4}-\d{2}-\d{2}T(\d{2}:\d{2})/)
  if (localDateTimeMatch) {
    return localDateTimeMatch[1]
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function taskStatusText(task) {
  if (!task) {
    return ''
  }
  if (task.taskStatus === 'pending') {
    return '任务已排队'
  }
  if (task.taskStatus === 'running') {
    return '正在检索与生成'
  }
  return task.progressStage || task.taskStatus || '处理中'
}

function estimateLearningMemoryChars() {
  return learningMemoryItems.value.reduce((total, item) => total + String(item.memoryText ?? '').length, 0)
}

function sourceTitle(source) {
  return source.sourceFile || source.headingPath || source.documentKey || `来源 ${source.rankPosition || ''}`.trim()
}

function sourceMeta(source) {
  const parts = []
  if (source.headingPath && source.headingPath !== source.sourceFile) {
    parts.push(source.headingPath)
  }
  const pageLabel = sourcePageLabel(source)
  if (pageLabel) {
    parts.push(pageLabel)
  }
  return parts.join(' · ')
}

function sourcePageLabel(source) {
  const start = Number(source.pageStart)
  const end = Number(source.pageEnd)
  if (Number.isFinite(start) && Number.isFinite(end) && start > 0 && end > 0 && start !== end) {
    return `第 ${start}-${end} 页`
  }
  if (Number.isFinite(start) && start > 0) {
    return `第 ${start} 页`
  }
  return ''
}

function sourceTypeLabel(source) {
  const type = source?.sourceType || 'unknown'
  if (type === 'bm25') {
    return 'BM25'
  }
  if (type === 'graphrag_citation') {
    return 'GraphRAG'
  }
  if (type === 'basic_citation') {
    return 'Basic'
  }
  if (type === 'fusion') {
    return 'Fusion'
  }
  return '来源'
}
</script>

<template>
  <div class="qa-ask-page">
    <!-- 主对话区 -->
    <div ref="mainRef" class="qa-main" aria-live="polite">
      <!-- 空态欢迎 -->
      <div v-if="isEmpty" class="empty-state">
        <div class="empty-icon">💬</div>
        <h1 class="empty-title">从真实课程知识库开始提问</h1>
        <p class="empty-desc">先写问题，系统会尝试识别课程。课程、模式、记忆等设置都在输入框左侧的 ＋ 菜单里。</p>
        <div class="suggest-row">
          <button class="suggest-chip" type="button" @click="input = '解释「进程」与「线程」的本质区别'">📘 解释「进程」与「线程」的本质区别</button>
          <button class="suggest-chip" type="button" @click="input = '给我整体的知识脉络'">🧭 给我整体的知识脉络</button>
          <button class="suggest-chip" type="button" @click="input = '第 3 章的例题怎么解？'">📍 第 3 章的例题怎么解？</button>
          <button class="suggest-chip" type="button" @click="input = '死锁与饥饿的关联与对比'">🔗 死锁与饥饿的关联与对比</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="msg.role === 'user' ? 'role-user' : 'role-assistant'"
        >
          <div v-if="msg.role === 'user'" class="bubble user-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-meta">
              <span>{{ formatMessageTime(msg.createdAt) }}</span>
              <span v-if="msg.taskStatus">{{ msg.taskStatus }}</span>
            </div>
          </div>

          <GlassCard v-else tier="base" padding="md" class="ai-bubble">
            <div class="msg-sources">
              <ModuleTag module="qa" size="sm">
                {{ selectedKnowledgeBase?.name || '课程知识库' }}
              </ModuleTag>
              <ModuleTag module="knowledge" size="sm">
                {{ activeModeOption.shortLabel }}
              </ModuleTag>
            </div>
            <QaMarkdownContent :content="msg.content" />
            <details v-if="msg.sources?.length" class="source-cards">
              <summary>参考来源 {{ msg.sources.length }}</summary>
              <ol class="source-list">
                <li
                  v-for="source in msg.sources"
                  :key="`${source.rankPosition}-${source.sourceRef || source.chunkId || source.documentKey}`"
                  class="source-card"
                >
                  <div class="source-card-head">
                    <span class="source-rank">来源 {{ source.rankPosition }}</span>
                    <span class="source-type">{{ sourceTypeLabel(source) }}</span>
                    <strong>{{ sourceTitle(source) }}</strong>
                  </div>
                  <div v-if="sourceMeta(source)" class="source-card-meta">{{ sourceMeta(source) }}</div>
                  <p v-if="source.snippet" class="source-snippet">{{ source.snippet }}</p>
                </li>
              </ol>
            </details>
            <div class="message-feedback" aria-label="回答反馈">
              <button
                v-for="action in FEEDBACK_ACTIONS"
                :key="action.key"
                class="feedback-action"
                :class="{ active: isFeedbackActionActive(msg, action) }"
                type="button"
                :disabled="feedbackSubmittingMessageId === msg.id"
                @click="handleFeedback(msg, action)"
              >
                {{ action.label }}
              </button>
            </div>
            <div class="msg-meta">{{ formatMessageTime(msg.createdAt) }}</div>
          </GlassCard>
        </div>

        <!-- 等待中 -->
        <div v-if="pendingTask" class="msg-row role-assistant">
          <GlassCard tier="base" padding="md" class="ai-bubble pending-bubble">
            <div class="pending-head">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>{{ taskStatusText(pendingTask) }}</span>
            </div>
            <div class="pending-copy">
              {{ pendingTask.routeReason || pendingTask.timeoutMessage || '后端正在执行 GraphRAG 查询任务。' }}
            </div>
            <QaMarkdownContent
              v-if="pendingTask.streamText"
              :content="pendingTask.streamText"
              class="pending-stream-content"
            />
            <div class="msg-meta">
              <el-icon><Clock /></el-icon>
              <span v-if="pendingTask.streaming">模式 {{ pendingTask.mode }}，事件流连接中</span>
              <span v-else>模式 {{ pendingTask.mode }}，轮询间隔 {{ resolvePollingDelaySeconds(pendingTask) }} 秒</span>
            </div>
          </GlassCard>
        </div>
      </template>
    </div>

    <!-- 底部 Composer -->
    <div class="composer-wrap">
      <!-- 浮层提示 -->
      <div v-if="errorMessage" class="toast error" role="alert">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-else-if="activeSessionReadOnlyMessage" class="toast error" role="alert">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ activeSessionReadOnlyMessage }}</span>
        <button
          v-if="isArchivedReadOnlySession(activeSession)"
          class="toast-action"
          type="button"
          @click="restoreActiveSession"
        >恢复会话</button>
        <button v-else class="toast-action" type="button" @click="startNewIndexedSession">新建会话</button>
      </div>
      <div v-else-if="activeIndexChanged" class="toast warn">
        <el-icon><WarningFilled /></el-icon>
        <span>知识库已有新索引；本会话继续使用旧索引 #{{ activeSession.indexRunId }}。</span>
        <button class="toast-action" type="button" @click="startNewIndexedSession">新建会话</button>
      </div>
      <div v-else-if="statusMessage" class="toast info">
        <el-icon><Search /></el-icon>
        <span>{{ statusMessage }}</span>
      </div>

      <div class="composer" :class="{ focused: composerFocused }">
        <textarea
          ref="composerInputRef"
          v-model="input"
          class="composer-input"
          :disabled="sending || restoringSession || Boolean(pendingTask) || Boolean(activeSessionReadOnlyMessage)"
          :placeholder="activeSessionReadOnlyMessage || '问点什么…'"
          rows="1"
          @focus="composerFocused = true"
          @blur="composerFocused = false"
          @keydown.enter.exact.prevent="send"
          @input="autoResizeInput"
        ></textarea>
        <div class="composer-toolbar">
          <!-- 左侧 Codex 圆环 -->
          <div class="plus-ring-wrap">
            <button
              class="plus-ring"
              :class="{ open: plusMenuOpen }"
              type="button"
              @click="plusMenuOpen = !plusMenuOpen"
            >＋</button>
            <Transition name="pop">
              <div v-if="plusMenuOpen" class="plus-menu">
                <div class="plus-menu-item disabled">
                  <span class="pm-ico">📎</span>
                  <span>添加照片和文件</span>
                  <span class="pm-badge">占位</span>
                </div>
                <div class="plus-menu-sep"></div>
                <div class="plus-menu-item" @click="handleMemoryToggle(!memoryEnabled)">
                  <span class="pm-ico">🧠</span>
                  <span>学习记忆</span>
                  <span class="pm-toggle" :class="memoryEnabled ? 'on' : 'off'"></span>
                </div>
                <div class="plus-menu-sep"></div>
                <div class="plus-menu-item" @click="courseSelectOpen = !courseSelectOpen; plusMenuOpen = false">
                  <span class="pm-ico">📚</span>
                  <span>课程</span>
                  <span class="pm-value">{{ selectedCourse?.name || '自动识别' }} ›</span>
                </div>
                <div class="plus-menu-item" @click="kbSelectOpen = !kbSelectOpen; plusMenuOpen = false">
                  <span class="pm-ico">🗂️</span>
                  <span>知识库</span>
                  <span class="pm-value">{{ selectedKnowledgeBase?.name || '自动选择' }} ›</span>
                </div>
              </div>
            </Transition>
          </div>

          <!-- 模式 chip -->
          <button class="chip mode-chip" type="button" @click="modeMenuOpen = !modeMenuOpen">
            ✨ {{ getModeOption(selectedMode).shortLabel === '智能' ? '智能' : getModeOption(selectedMode).shortLabel }}
            <span class="chip-arrow">▾</span>
          </button>

          <!-- 课程选择 popover -->
          <Transition name="pop">
            <div v-if="courseSelectOpen" class="scope-popover">
              <div class="scope-pop-head">
                <span>选择课程</span>
                <button class="scope-pop-close" type="button" @click="courseSelectOpen = false">✕</button>
              </div>
              <div class="scope-pop-list">
                <div
                  class="scope-pop-item"
                  :class="{ active: !selectedCourseId }"
                  @click="handleCourseChange(''); courseSelectOpen = false"
                >
                  <span>自动识别</span>
                  <span class="scope-pop-hint">根据问题内容自动匹配</span>
                </div>
                <div
                  v-for="course in courses"
                  :key="course.courseId"
                  class="scope-pop-item"
                  :class="{ active: selectedCourseId === course.courseId }"
                  @click="selectedCourseId = course.courseId; handleCourseChange(course.courseId); courseSelectOpen = false"
                >
                  <span>{{ course.name }}</span>
                </div>
              </div>
            </div>
          </Transition>

          <!-- 知识库选择 popover -->
          <Transition name="pop">
            <div v-if="kbSelectOpen" class="scope-popover">
              <div class="scope-pop-head">
                <span>选择知识库</span>
                <button class="scope-pop-close" type="button" @click="kbSelectOpen = false">✕</button>
              </div>
              <div v-if="!selectedCourseId" class="scope-pop-empty">请先选择课程</div>
              <div v-else-if="!knowledgeBases.length" class="scope-pop-empty">该课程暂无知识库</div>
              <div v-else class="scope-pop-list">
                <div
                  v-for="kb in knowledgeBases"
                  :key="kb.id"
                  class="scope-pop-item"
                  :class="{ active: String(selectedKnowledgeBaseId) === String(kb.id), disabled: kb.activeIndexRunId == null }"
                  @click="kb.activeIndexRunId != null && (selectedKnowledgeBaseId = String(kb.id), handleKnowledgeBaseChange(), kbSelectOpen = false)"
                >
                  <span>{{ kb.name }}</span>
                  <span class="scope-pop-hint">{{ kb.activeIndexRunId == null ? '未激活索引' : '索引 #' + kb.activeIndexRunId }}</span>
                </div>
              </div>
            </div>
          </Transition>

          <!-- 模式选择 popover -->
          <Transition name="pop">
            <div v-if="modeMenuOpen" class="mode-popover">
              <div
                v-for="mode in QA_MODE_OPTIONS"
                :key="mode.value"
                class="mode-pop-item"
                :class="{ active: selectedMode === mode.value }"
                @click="handleModeSelect(mode.value); modeMenuOpen = false"
              >
                <span class="mode-pop-label">{{ mode.label }}</span>
                <span class="mode-pop-desc">{{ mode.description }}</span>
              </div>
              <div class="mode-pop-foot">
                <label>
                  <input v-model="allowHybridSmartBeta" type="checkbox" @change="handleHybridBetaToggle" />
                  允许智能推荐使用混合检索 Beta
                </label>
              </div>
            </div>
          </Transition>

          <span class="toolbar-spacer"></span>

          <!-- 学习记忆（开启后才显示） -->
          <button v-if="memoryEnabled" class="chip memory-chip" type="button">
            🧠 学习记忆 · {{ learningMemoryItems.length }}
          </button>

          <!-- 发送 -->
          <button
            class="send-btn"
            :class="{ disabled: !canSend }"
            :disabled="!canSend"
            type="button"
            aria-label="发送问题"
            @click="send"
          >
            <el-icon v-if="sending" class="is-loading" :size="16"><Loading /></el-icon>
            <span v-else class="send-arrow">➤</span>
          </button>
        </div>
      </div>

      <div class="composer-hint">
        <span><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行</span>
        <span v-if="selectedCourse">{{ selectedCourse.name }} · {{ activeModeOption.shortLabel }}</span>
        <span v-else>可先提问，系统会尝试识别课程</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.qa-ask-page {
  --qa-primary: #9333ea;
  --qa-primary-strong: #7e22ce;
  --qa-teal: #0d9488;

  position: relative;
  display: flex;
  min-height: calc(100vh - 64px);
  flex-direction: column;
  background:
    radial-gradient(1200px 600px at 80% -10%, rgba(168, 85, 247, 0.06), transparent 60%),
    radial-gradient(900px 500px at -5% 110%, rgba(99, 102, 241, 0.04), transparent 60%),
    #f8fafc;
}

/* ===== 主对话区 ===== */
.qa-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  padding: 16px 24px;
  scroll-behavior: smooth;

  &::-webkit-scrollbar { width: 8px; }
  &::-webkit-scrollbar-thumb { background: rgba(100, 116, 139, 0.25); border-radius: 4px; }
}

/* ===== 空态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  flex: 1;
  padding: 32px 24px;
  gap: 14px;
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: linear-gradient(135deg, #9333ea, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  box-shadow: 0 18px 40px rgba(147, 51, 234, 0.32);
}

.empty-title {
  font-family: 'Space Grotesk', inherit;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #0f172a, #7e22ce 90%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.empty-desc {
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
  max-width: 460px;
}

.suggest-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 780px;
  margin-top: 8px;
}

.suggest-chip {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(226, 232, 240, 0.9);
  color: #475569;
  font-size: 12.5px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  cursor: pointer;
  font-family: inherit;
  transition: border-color $duration-fast $ease-out, color $duration-fast $ease-out, transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;
  backdrop-filter: blur(10px);

  &:hover {
    border-color: rgba(147, 51, 234, 0.4);
    color: #7e22ce;
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba(147, 51, 234, 0.1);
  }
}

/* ===== 消息气泡 ===== */
.msg-row { display: flex; }
.role-user { justify-content: flex-end; }
.role-assistant { justify-content: flex-start; }

.user-bubble {
  max-width: min(680px, 76%);
  padding: 12px 16px;
  border-radius: 16px 16px 4px 16px;
  background: linear-gradient(135deg, var(--qa-primary), #a855f7);
  box-shadow: 0 12px 28px rgba(147, 51, 234, 0.22);
  color: #fff;
}

.ai-bubble {
  max-width: min(760px, 82%);
  border-color: rgba(147, 51, 234, 0.18) !important;
  border-radius: 16px 16px 16px 4px !important;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.msg-sources { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.msg-text { font-size: 14px; line-height: 1.7; white-space: pre-wrap; }

.source-cards { margin-top: 12px; border-top: 1px solid rgba(148, 163, 184, 0.22); padding-top: 10px; }
.source-cards summary { width: max-content; cursor: pointer; color: #475569; font-size: 12px; font-weight: 800; }
.source-list { display: grid; gap: 8px; margin: 10px 0 0; padding: 0; list-style: none; }
.source-card { border: 1px solid rgba(148, 163, 184, 0.24); border-radius: $radius-md; background: rgba(248, 250, 252, 0.82); padding: 9px 10px; }
.source-card-head { display: flex; align-items: center; gap: 8px; color: #1f2937; font-size: 12px; }
.source-card-head strong { min-width: 0; overflow-wrap: anywhere; }
.source-rank { flex: 0 0 auto; border-radius: 999px; background: rgba(20, 184, 166, 0.13); padding: 2px 7px; color: #0f766e; font-size: 11px; font-weight: 800; }
.source-type { flex: 0 0 auto; border-radius: 999px; background: rgba(147, 51, 234, 0.1); padding: 2px 7px; color: #7e22ce; font-size: 11px; font-weight: 800; }
.source-card-meta { margin-top: 5px; color: #64748b; font-size: 11px; line-height: 1.55; }
.source-snippet { margin: 7px 0 0; color: #475569; font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; }

.message-feedback { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.feedback-action {
  height: 26px; padding: 0 10px; border-radius: 999px; font-size: 11.5px; font-weight: 700;
  background: rgba(248, 250, 252, 0.95); border: 1px solid rgba(226, 232, 240, 0.85);
  color: #64748b; cursor: pointer; font-family: inherit;
  transition: border-color $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover:not(:disabled) { border-color: rgba(147, 51, 234, 0.4); color: var(--qa-primary-strong); }
  &.active { background: rgba(20, 184, 166, 0.12); color: #0f766e; border-color: rgba(20, 184, 166, 0.32); }
  &:disabled { cursor: wait; opacity: 0.6; }
}

.msg-meta { display: inline-flex; align-items: center; gap: 8px; margin-top: 8px; color: #94a3b8; font-size: 11px; }
.user-bubble .msg-meta { color: rgba(255, 255, 255, 0.78); }

.pending-bubble { border-color: rgba(13, 148, 136, 0.28) !important; }
.pending-head { display: inline-flex; align-items: center; gap: 8px; color: var(--qa-teal); font-size: 13px; font-weight: 800; }
.pending-copy { margin-top: 8px; color: #475569; font-size: 13px; line-height: 1.65; }
.pending-stream-content { margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(148, 163, 184, 0.18); }

.is-loading { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== Composer ===== */
.composer-wrap {
  position: sticky;
  bottom: 0;
  padding: 16px 24px 20px;
  background: linear-gradient(180deg, transparent, rgba(248, 250, 252, 0.95) 30%);
}

.toast {
  max-width: 820px;
  margin: 0 auto 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 12.5px;
  font-weight: 600;
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);

  &.info { background: rgba(239, 246, 255, 0.88); border: 1px solid rgba(59, 130, 246, 0.28); color: #1d4ed8; }
  &.warn { background: rgba(255, 251, 235, 0.9); border: 1px solid rgba(245, 158, 11, 0.32); color: #b45309; }
  &.error { background: rgba(254, 242, 242, 0.92); border: 1px solid rgba(239, 68, 68, 0.32); color: #b91c1c; }
}

.toast-action {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.composer {
  max-width: 820px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid rgba(147, 51, 234, 0.22);
  border-radius: 24px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset, 0 22px 60px rgba(15, 23, 42, 0.08), 0 0 0 6px rgba(147, 51, 234, 0.06);
  overflow: visible;
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &.focused {
    border-color: rgba(147, 51, 234, 0.5);
    box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset, 0 28px 70px rgba(147, 51, 234, 0.18), 0 0 0 6px rgba(147, 51, 234, 0.1);
  }
}

.composer-input {
  display: block;
  width: 100%;
  padding: 18px 22px 6px;
  min-height: 48px;
  max-height: 200px;
  font-size: 15px;
  line-height: 1.6;
  color: #0f172a;
  border: 0;
  outline: 0;
  background: transparent;
  resize: none;
  font-family: inherit;
  overflow-y: auto;

  &::placeholder { color: #94a3b8; }
  &:disabled { cursor: not-allowed; opacity: 0.6; }
}

.composer-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px 12px;
  flex-wrap: wrap;
  position: relative;
}

.toolbar-spacer { flex: 1; }

/* Codex 圆环 */
.plus-ring-wrap { position: relative; }

.plus-ring {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid rgba(147, 51, 234, 0.35);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #7e22ce;
  font-size: 18px;
  font-weight: 300;
  font-family: inherit;
  transition: transform $duration-fast $ease-out, border-color $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover { border-color: rgba(147, 51, 234, 0.7); background: rgba(250, 245, 255, 0.6); transform: scale(1.06); }
  &.open { border-color: #9333ea; background: rgba(250, 245, 255, 0.9); transform: rotate(45deg); }
}

.plus-menu {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 0;
  width: 260px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
  padding: 6px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.plus-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.06); color: #7e22ce; }
  &.disabled { opacity: 0.5; cursor: not-allowed; &:hover { background: transparent; color: #334155; } }
}

.pm-ico { width: 20px; text-align: center; font-size: 15px; }
.pm-badge { margin-left: auto; font-size: 11px; color: #94a3b8; font-weight: 700; }
.pm-value { margin-left: auto; font-size: 12px; color: #0f172a; font-weight: 800; }

.pm-toggle {
  margin-left: auto;
  width: 36px;
  height: 20px;
  border-radius: 999px;
  position: relative;
  transition: background $duration-fast $ease-out;

  &.off { background: rgba(148, 163, 184, 0.3); }
  &.on { background: #9333ea; }

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
    transition: left $duration-fast $ease-out, right $duration-fast $ease-out;
  }

  &.off::after { left: 2px; }
  &.on::after { left: auto; right: 2px; }
}

.plus-menu-sep { height: 1px; background: rgba(226, 232, 240, 0.8); margin: 4px 8px; }

/* Chip 按钮 */
.chip {
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: #fff;
  color: #475569;
  font-size: 12.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  font-family: inherit;
  transition: border-color $duration-fast $ease-out, color $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover { border-color: rgba(147, 51, 234, 0.4); color: #7e22ce; }
}

.chip-arrow { color: #94a3b8; font-size: 10px; }

.mode-chip {
  background: linear-gradient(135deg, #faf5ff, #fff);
  border-color: rgba(147, 51, 234, 0.32);
  color: #7e22ce;
}

.memory-chip {
  background: rgba(240, 253, 250, 0.6);
  border-color: rgba(13, 148, 136, 0.28);
  color: #0f766e;
}

/* 模式 popover */
.mode-popover {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 48px;
  width: 320px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
  padding: 6px;
  z-index: 10;
}

.mode-pop-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.06); }
  &.active { background: rgba(147, 51, 234, 0.1); }
}

.mode-pop-label { font-size: 13px; color: #0f172a; font-weight: 700; }
.mode-pop-desc { font-size: 11.5px; color: #64748b; line-height: 1.5; }

.mode-pop-foot {
  border-top: 1px solid rgba(226, 232, 240, 0.8);
  margin-top: 6px;
  padding: 8px 12px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;

  input { accent-color: #9333ea; width: 13px; height: 13px; }
  label { display: flex; align-items: center; gap: 6px; cursor: pointer; }
}

/* 课程/知识库选择 popover */
.scope-popover {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 48px;
  width: 300px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
  padding: 6px;
  z-index: 12;
}

.scope-pop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 6px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
}

.scope-pop-close {
  border: 0;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;

  &:hover { color: #475569; }
}

.scope-pop-list {
  max-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.scope-pop-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.06); }
  &.active { background: rgba(147, 51, 234, 0.1); color: #7e22ce; }
  &.disabled { opacity: 0.5; cursor: not-allowed; &:hover { background: transparent; } }
}

.scope-pop-hint {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.scope-pop-empty {
  padding: 16px 12px;
  text-align: center;
  color: #94a3b8;
  font-size: 12.5px;
}

/* 发送按钮 */
.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 0;
  cursor: pointer;
  background: linear-gradient(135deg, #9333ea, #6366f1);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px rgba(147, 51, 234, 0.36);
  font-size: 16px;
  font-family: inherit;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover:not(:disabled) { transform: translateY(-1px) scale(1.04); box-shadow: 0 16px 36px rgba(147, 51, 234, 0.46); }
  &.disabled, &:disabled { background: #cbd5e1; box-shadow: none; cursor: not-allowed; opacity: 0.7; }
}

.send-arrow {
  font-size: 18px;
  font-weight: 700;
  line-height: 1;
  display: inline-block;
  transform: rotate(-90deg);
}

.composer-hint {
  max-width: 820px;
  margin: 8px auto 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #94a3b8;
  font-size: 11.5px;

  kbd {
    font-family: 'JetBrains Mono', ui-monospace, monospace;
    background: rgba(148, 163, 184, 0.16);
    border-radius: 4px;
    padding: 1px 5px;
    font-size: 10.5px;
    color: #475569;
  }
}

/* ===== Transitions ===== */
.pop-enter-active, .pop-leave-active { transition: opacity $duration-fast $ease-out, transform $duration-fast $ease-out; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(6px) scale(0.96); }

/* ===== 响应式 ===== */
@media (max-width: 980px) {
  .user-bubble, .ai-bubble { max-width: 100%; }
  .composer { border-radius: 18px; }
}
</style>

