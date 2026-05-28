<!-- frontend/apps/student-app/src/views/qa/index.vue -->
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
  checkQaQuestionDomain,
  createQaSession,
  deleteQaMemoryItem,
  deleteQaFeedback,
  getQaMemoryPreference,
  getQaSession,
  listQaMemoryItems,
  getQaTask,
  listQaMessages,
  recommendCourse,
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
import {
  buildQaRouteQuery,
  normalizeQaRouteQuery,
  withoutQaSessionQuery,
} from './qa-route-query-model'
import QaMarkdownContent from './QaMarkdownContent.vue'
import QaRetrievalTrace from './QaRetrievalTrace.vue'
import { mergeRetrievalTraceEvents } from './qa-retrieval-trace-model'
import {
  isNearScrollBottom,
  resolveAutoScrollAfterUserScroll,
  shouldAutoFollowNewContent,
} from './qa-scroll-model'
import {
  isTerminalTaskStatus,
  hasActiveIndexChanged,
  isArchivedReadOnlySession,
  isLegacyReadOnlySession,
  isQuestionDomainOutOfScope,
  learningMemoryTypeLabel,
  normalizeQaQuestionDomainCheck,
  normalizeCourseRoutingRecommendation,
  normalizeCourseList,
  normalizeKnowledgeBaseList,
  normalizeLearningMemory,
  normalizeMemoryPreference,
  normalizeQaFeedback,
  normalizeQaMessage,
  normalizeQaSession,
  normalizeProgressEvents,
  normalizeStreamEventSeq,
  normalizeTaskLogs,
  resolveSessionLifecycleStatusText,
  resolveContextStatusText,
  resolveMemoryStatusText,
  resolvePollingDelaySeconds,
  selectReadyKnowledgeBase,
  shouldRequestCourseRouting,
  upsertQaMessage,
} from './qa-session-model'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mainRef = ref(null)
const initialQaRouteQuery = normalizeQaRouteQuery(route.query)
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
const selectedMode = ref(initialQaRouteQuery.mode)
const allowHybridSmartBeta = ref(false)
const memoryPreference = ref(normalizeMemoryPreference(null))
const learningMemoryItems = ref([])
const input = ref(initialQaRouteQuery.topic)
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
const courseRoutingLoading = ref(false)
const courseRouteConfirmation = ref(null)
const feedbackSubmittingMessageId = ref(null)
const errorMessage = ref('')
const statusMessage = ref('')
const memoryErrorMessage = ref('')
const autoScrollPinned = ref(true)
const showJumpToLatest = ref(false)
let pollTimer = null
let taskStreamController = null
let knowledgeBaseLoadRequestId = 0
let sessionRestoreRequestId = 0

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
const pendingProcessEvents = computed(() => normalizeProgressEvents(
  pendingTask.value?.progressEvents ?? pendingTask.value?.latestLogs,
))
const hasActivePendingTask = computed(() => Boolean(
  pendingTask.value && !isTerminalTaskStatus(pendingTask.value.taskStatus),
))
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
  && !courseRoutingLoading.value
  && !courseRouteConfirmation.value
  && !hasActivePendingTask.value
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

watch(
  () => {
    const query = normalizeQaRouteQuery(route.query)
    return [query.courseId, query.sessionId, query.mode, query.topic]
  },
  async ([courseId, sessionId, mode, topic], [previousCourseId, previousSessionId, previousMode, previousTopic]) => {
    if (mode !== previousMode && selectedMode.value !== mode) {
      selectedMode.value = mode
    }

    if (!sessionId && topic !== previousTopic && canSyncTopicPrefill(previousTopic)) {
      input.value = topic
    }

    if (sessionId) {
      if (String(activeSession.value?.id ?? '') !== sessionId) {
        await restoreSessionFromQuery()
      }
      return
    }

    if (previousSessionId && !sessionId) {
      resetConversation()
      if (courseId !== selectedCourseId.value) {
        await syncCourseScopeFromQuery(courseId)
      }
      return
    }

    if (courseId !== previousCourseId) {
      resetConversation()
      await syncCourseScopeFromQuery(courseId)
    }
  },
)

watch(input, (value) => {
  if (courseRouteConfirmation.value && value.trim() !== courseRouteConfirmation.value.question) {
    clearCourseRouteConfirmation()
  }
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
    const queryCourseId = normalizeQaRouteQuery(route.query).courseId
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
  const requestId = ++knowledgeBaseLoadRequestId
  const requestCourseId = String(courseId || '')

  if (!courseId) {
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    resetMemoryState()
    loadingKnowledgeBases.value = false
    return []
  }

  loadingKnowledgeBases.value = true
  errorMessage.value = ''
  try {
    const payload = await listCourseKnowledgeBases(requestCourseId)
    if (
      requestId !== knowledgeBaseLoadRequestId
      || requestCourseId !== String(selectedCourseId.value || '')
    ) {
      return []
    }
    const nextKnowledgeBases = normalizeKnowledgeBaseList(payload)
    knowledgeBases.value = nextKnowledgeBases
    const selected = selectReadyKnowledgeBase(nextKnowledgeBases, selectedKnowledgeBaseId.value)
    selectedKnowledgeBaseId.value = selected.knowledgeBase?.id ? String(selected.knowledgeBase.id) : ''
    if (options.loadMemory !== false) {
      await loadMemoryState(requestCourseId, selectedKnowledgeBaseId.value)
    }
    return nextKnowledgeBases
  } catch (error) {
    if (
      requestId !== knowledgeBaseLoadRequestId
      || requestCourseId !== String(selectedCourseId.value || '')
    ) {
      return []
    }
    errorMessage.value = error?.message || '知识库列表加载失败'
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    resetMemoryState()
    return []
  } finally {
    if (requestId === knowledgeBaseLoadRequestId) {
      loadingKnowledgeBases.value = false
    }
  }
}

async function syncCourseScopeFromQuery(courseId) {
  if (!courseId) {
    selectedCourseId.value = ''
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    resetMemoryState()
    return
  }

  selectedCourseId.value = courseId
  selectedKnowledgeBaseId.value = ''
  await loadKnowledgeBases(courseId)
}

async function handleCourseChange(courseId) {
  clearCourseRouteConfirmation()
  resetConversation()
  resetMemoryState()
  selectedKnowledgeBaseId.value = ''
  await router.replace({
    path: route.path,
    query: buildQaRouteQuery(route.query, {
      courseId,
      sessionId: '',
    }),
  })
}

async function handleKnowledgeBaseChange() {
  resetConversation()
  await loadMemoryState(selectedCourseId.value, selectedKnowledgeBaseId.value)
  await clearSessionQuery()
}

function canSyncTopicPrefill(previousTopic) {
  if (activeSession.value) {
    return false
  }
  return !input.value.trim() || input.value === previousTopic
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
  if (!text || sending.value || hasActivePendingTask.value) {
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
  if (courseRouteConfirmation.value?.question !== text) {
    clearCourseRouteConfirmation()
  }

  try {
    if (!courses.value.length) {
      await loadCourses()
    }
    const course = await resolveCourse(text)
    if (!course) {
      return
    }
    const nextKnowledgeBases = knowledgeBases.value.length
      ? knowledgeBases.value
      : await loadKnowledgeBases(course.courseId, { loadMemory: false })
    const knowledgeBaseResult = selectReadyKnowledgeBase(nextKnowledgeBases, selectedKnowledgeBaseId.value)
    if (knowledgeBaseResult.status !== 'ready') {
      throw new Error('当前课程还没有可用知识库索引，请先完成知识库构建')
    }

    const knowledgeBase = knowledgeBaseResult.knowledgeBase
    selectedCourseId.value = course.courseId
    selectedKnowledgeBaseId.value = String(knowledgeBase.id)
    const domainGuardResult = await checkQuestionDomainBeforeSend(course, knowledgeBase, text)
    if (domainGuardResult.blocked) {
      errorMessage.value = domainGuardResult.message
      statusMessage.value = ''
      ElMessage.warning(domainGuardResult.message)
      return
    }
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

    messages.value = upsertQaMessage(messages.value, withTaskMode(submission.userMessage, { mode: modeResolution.mode }))
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
    const submitStatus = modeResolution.fromSmart
      ? `智能推荐为 ${modeResolution.mode} 模式：${modeResolution.reason}。${resolveContextStatusText(submission)}。${memoryStatus}`
      : `已使用 ${modeResolution.mode} 模式提交问题。${resolveContextStatusText(submission)}。${memoryStatus}`
    const domainGuardHint = domainGuardResult.statusMessage ? `${domainGuardResult.statusMessage}。` : ''
    statusMessage.value = `${domainGuardHint}${submitStatus}${confidenceHint}`
    input.value = ''
    await scrollToBottom({ force: true })
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

async function checkQuestionDomainBeforeSend(course, knowledgeBase, text) {
  try {
    const result = normalizeQaQuestionDomainCheck(await checkQaQuestionDomain({
      courseId: course.courseId,
      knowledgeBaseId: knowledgeBase.id,
      sessionId: activeSession.value?.id,
      question: text,
      hasConversationContext: messages.value.length > 0 || Boolean(activeSession.value),
    }))
    if (isQuestionDomainOutOfScope(result)) {
      return {
        blocked: true,
        message: result.message,
        statusMessage: '',
      }
    }
    return {
      blocked: false,
      message: '',
      statusMessage: '',
    }
  } catch {
    return {
      blocked: false,
      message: '',
      statusMessage: '课程问答范围校验暂不可用，已继续提交',
    }
  }
}

async function handleModeSelect(mode) {
  selectedMode.value = mode
  await router.replace({
    path: route.path,
    query: buildQaRouteQuery(route.query, { mode }),
  })
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
  if (!shouldRequestCourseRouting({
    selectedCourseId: selectedCourseId.value,
    sessionCourseId: activeSession.value?.courseId,
  })) {
    if (selectedCourse.value) {
      return selectedCourse.value
    }
    throw new Error('当前课程不可用，请刷新课程或重新选择')
  }

  courseRoutingLoading.value = true
  try {
    const recommendation = normalizeCourseRoutingRecommendation(await recommendCourse({
      question,
      userId: userStore.user.id,
      limit: 3,
    }))

    if (recommendation.status === 'matched') {
      const course = resolveCourseById(recommendation.selectedCourseId, recommendation.candidates[0])
      if (!course) {
        throw new Error('已识别课程，但当前课程列表不可用，请刷新课程后重试')
      }
      selectedCourseId.value = course.courseId
      clearCourseRouteConfirmation()
      statusMessage.value = `已根据课程画像识别课程：${course.name}`
      await loadKnowledgeBases(course.courseId, { loadMemory: false })
      return course
    }

    if (recommendation.status === 'needs_confirmation' && recommendation.candidates.length) {
      courseRouteConfirmation.value = {
        question,
        candidates: recommendation.candidates,
        confidence: recommendation.confidence,
        margin: recommendation.margin,
      }
      statusMessage.value = '课程画像匹配不够确定，请确认候选课程'
      return null
    }

    throw new Error('暂未识别到课程，请先选择课程后提问')
  } catch (error) {
    throw new Error(error?.message || '课程识别暂不可用，请先手动选择课程')
  } finally {
    courseRoutingLoading.value = false
  }
}

async function handleCourseRouteCandidateConfirm(candidate) {
  const course = resolveCourseById(candidate?.courseId, candidate)
  if (!course) {
    errorMessage.value = '候选课程不可用，请刷新课程后重试'
    return
  }
  selectedCourseId.value = course.courseId
  clearCourseRouteConfirmation()
  statusMessage.value = `已确认课程：${course.name}`
  await loadKnowledgeBases(course.courseId)
  if (input.value.trim()) {
    await send()
  }
}

function resolveCourseById(courseId, fallback = null) {
  const normalizedCourseId = String(courseId || '')
  if (!normalizedCourseId) {
    return null
  }
  return courses.value.find((course) => course.courseId === normalizedCourseId) ?? (
    fallback
      ? {
          id: normalizedCourseId,
          courseId: normalizedCourseId,
          name: fallback.name || fallback.courseName || normalizedCourseId,
          description: '',
          activeKnowledgeBaseCount: 0,
          latestIndexRunId: null,
          status: '',
        }
      : null
  )
}

function clearCourseRouteConfirmation() {
  courseRouteConfirmation.value = null
}

function formatConfidence(value) {
  const score = Number(value)
  return Number.isFinite(score) ? `${Math.round(score * 100)}%` : '0%'
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
    query: buildQaRouteQuery(route.query, {
      courseId: course.courseId,
      sessionId: String(activeSession.value.id),
    }),
  })
  return activeSession.value
}

async function restoreSessionFromQuery(routeSessionId = normalizeQaRouteQuery(route.query).sessionId) {
  const sessionId = String(routeSessionId || '')
  if (!sessionId) {
    return
  }

  const requestId = ++sessionRestoreRequestId
  restoringSession.value = true
  clearPollTimer()
  clearTaskStream()
  pendingTask.value = null
  errorMessage.value = ''
  try {
    const session = normalizeQaSession(await getQaSession(sessionId))
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    activeSession.value = session
    selectedCourseId.value = session.courseId
    await syncRestoredSessionRouteQuery(session, sessionId)
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    if (session.courseId) {
      await loadKnowledgeBases(session.courseId, { loadMemory: false })
    } else {
      knowledgeBases.value = []
      selectedKnowledgeBaseId.value = ''
    }
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    selectedKnowledgeBaseId.value = session.knowledgeBaseId != null ? String(session.knowledgeBaseId) : ''
    await loadMemoryState(session.courseId, selectedKnowledgeBaseId.value)
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    const list = await listQaMessages(session.id)
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    messages.value = list.map(normalizeQaMessage)
    const resumed = await resumeRunningTaskFromMessages(session.id, messages.value, requestId, sessionId)
    if (!isCurrentSessionRestore(requestId, sessionId)) {
      return
    }
    if (!resumed) {
      const restoredTerminalPartial = restoreTerminalPartialTaskFromMessages(session.id, messages.value)
      statusMessage.value = restoredTerminalPartial
        ? '已恢复上次未完成任务的部分回答和检索过程，可以继续提问'
        : (activeSessionReadOnlyMessage.value || '已恢复历史会话，可以继续提问')
    }
    await scrollToBottom({ force: true, behavior: 'auto' })
  } catch (error) {
    if (isCurrentSessionRestore(requestId, sessionId)) {
      errorMessage.value = error?.message || '历史会话恢复失败'
    }
  } finally {
    if (requestId === sessionRestoreRequestId) {
      restoringSession.value = false
    }
  }
}

async function resumeRunningTaskFromMessages(sessionId, restoredMessages, requestId, routeSessionId) {
  const runningUserMessage = [...restoredMessages].reverse().find((message) => {
    const status = String(message.taskStatus ?? '').toLowerCase()
    return message.role === 'user' && message.taskId && ['pending', 'running'].includes(status)
  })
  if (!runningUserMessage) {
    return false
  }

  try {
    const detail = await getQaTask(sessionId, runningUserMessage.taskId)
    if (!isCurrentSessionRestore(requestId, routeSessionId)) {
      return true
    }
    updateUserMessageTask(detail)
    if (isTerminalTaskStatus(detail.taskStatus)) {
      pendingTask.value = null
      if (detail.taskStatus === 'success' && detail.assistantMessage) {
        messages.value = upsertQaMessage(messages.value, withTaskMode(detail.assistantMessage, detail))
        statusMessage.value = '回答已生成，可以继续提问'
      } else if (detail.taskStatus !== 'success') {
        errorMessage.value = detail.errorMessage || detail.timeoutMessage || '问答任务已结束'
      }
      return false
    }

    const progressEvents = mergeProgressEvents(
      runningUserMessage.progressEvents,
      detail.progressEvents ?? detail.progress_events ?? detail.latestLogs,
    )
    pendingTask.value = {
      ...detail,
      sessionId,
      mode: detail.mode || runningUserMessage.mode || '',
      queryText: detail.queryText || runningUserMessage.content,
      streamText: detail.partialResponseText || runningUserMessage.partialResponseText || '',
      progressEvents,
      latestLogs: normalizeTaskLogs(detail.latestLogs ?? runningUserMessage.latestLogs, progressEvents),
      lastStreamEventSeq: normalizeStreamEventSeq(detail.streamEventSeq ?? runningUserMessage.streamEventSeq),
    }
    statusMessage.value = '已恢复运行中的问答任务，正在继续接收结果'
    startTaskStream(sessionId, runningUserMessage.taskId, pendingTask.value)
    return true
  } catch (error) {
    if (isCurrentSessionRestore(requestId, routeSessionId)) {
      statusMessage.value = '已恢复历史会话，运行中任务状态暂时无法校准'
    }
    return false
  }
}

function restoreTerminalPartialTaskFromMessages(sessionId, restoredMessages) {
  const lastMessage = restoredMessages.at(-1)
  if (lastMessage?.role !== 'user' || !lastMessage.taskId) {
    return false
  }
  const status = String(lastMessage.taskStatus ?? '').toLowerCase()
  if (!['failed', 'stale'].includes(status)) {
    return false
  }
  const streamText = lastMessage.partialResponseText || ''
  const progressEvents = normalizeProgressEvents(lastMessage.progressEvents ?? lastMessage.latestLogs)
  const latestLogs = normalizeTaskLogs(lastMessage.latestLogs, progressEvents)
  if (!streamText && !progressEvents.length && !latestLogs.length) {
    return false
  }
  pendingTask.value = {
    sessionId,
    taskId: lastMessage.taskId,
    taskStatus: status,
    progressStage: lastMessage.progressStage,
    mode: lastMessage.mode || '',
    queryText: lastMessage.content,
    streamText,
    latestLogs,
    progressEvents,
    streamEventSeq: normalizeStreamEventSeq(lastMessage.streamEventSeq),
    lastStreamEventSeq: normalizeStreamEventSeq(lastMessage.streamEventSeq),
    streaming: false,
    timeoutMessage: status === 'stale' ? '任务心跳超时，已保留已生成的部分回答。' : '',
  }
  return true
}

async function syncRestoredSessionRouteQuery(session, sessionId) {
  if (!session.courseId || normalizeQaRouteQuery(route.query).courseId === session.courseId) {
    return
  }
  await router.replace({
    path: route.path,
    query: buildQaRouteQuery(route.query, {
      courseId: session.courseId,
      sessionId,
    }),
  })
}

function isCurrentSessionRestore(requestId, sessionId) {
  return (
    requestId === sessionRestoreRequestId
    && normalizeQaRouteQuery(route.query).sessionId === String(sessionId)
  )
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
  const query = withoutQaSessionQuery(route.query)
  if (!query.courseId && selectedCourseId.value) {
    query.courseId = selectedCourseId.value
  }
  await router.replace({ path: route.path, query })
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
  const initialStreamText = String(
    submission?.streamText
    ?? submission?.partialResponseText
    ?? '',
  )
  const initialEventSeq = normalizeStreamEventSeq(
    submission?.lastStreamEventSeq
    ?? submission?.streamEventSeq,
  )
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
  const isCurrentStream = () => (
    taskStreamController === controller
    && String(activeSession.value?.id ?? '') === String(sessionId)
  )

  const fallbackToPolling = (reason = '事件流暂不可用，已切换为轮询。') => {
    if (state.terminal || state.fallbackStarted || controller.signal.aborted || !isCurrentStream()) {
      return
    }
    state.fallbackStarted = true
    statusMessage.value = reason
    scheduleTaskPoll(sessionId, taskId, Math.min(2, resolvePollingDelaySeconds(pendingTask.value || submission)))
  }

  streamQaTaskEvents(sessionId, taskId, {
    open() {
      if (!isCurrentStream()) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        streamText: pendingTask.value?.streamText ?? initialStreamText,
        lastStreamEventSeq: normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? initialEventSeq),
      }
    },
    status(payload) {
      if (!isCurrentStream()) {
        return
      }
      if (!payload) {
        return
      }
      const currentStreamText = pendingTask.value?.streamText ?? initialStreamText
      const payloadPartialText = payload.partialResponseText ?? payload.partial_result_text ?? ''
      const currentSeq = normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? initialEventSeq)
      const payloadSeq = normalizeStreamEventSeq(payload.streamEventSeq ?? payload.stream_event_seq)
      const progressEvents = mergeProgressEvents(
        pendingTask.value?.progressEvents,
        payload.progressEvents ?? payload.progress_events ?? payload.latestLogs,
      )
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        ...payload,
        sessionId,
        streaming: true,
        streamText: currentStreamText || payloadPartialText,
        progressEvents,
        latestLogs: normalizeTaskLogs(payload.latestLogs ?? pendingTask.value?.latestLogs, progressEvents),
        lastStreamEventSeq: Math.max(currentSeq, payloadSeq),
      }
      updateUserMessageTask(payload)
    },
    heartbeat() {
      if (!isCurrentStream()) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        lastStreamHeartbeatAt: new Date().toISOString(),
      }
    },
    progress(payload) {
      if (!isCurrentStream()) {
        return
      }
      const eventSeq = normalizeStreamEventSeq(payload?.eventSeq ?? payload?.event_seq)
      const lastEventSeq = normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? initialEventSeq)
      const nextEventSeq = Math.max(lastEventSeq, eventSeq)
      const progressEvents = mergeProgressEvents(pendingTask.value?.progressEvents, [payload])
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        progressEvents,
        latestLogs: normalizeTaskLogs(pendingTask.value?.latestLogs, progressEvents),
        lastStreamEventSeq: nextEventSeq,
        streamEventSeq: nextEventSeq,
      }
    },
    delta(payload) {
      if (!isCurrentStream()) {
        return
      }
      const text = payload?.text ?? ''
      if (!text) {
        return
      }
      const eventSeq = normalizeStreamEventSeq(payload?.eventSeq ?? payload?.event_seq)
      const lastEventSeq = normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? initialEventSeq)
      if (eventSeq > 0 && eventSeq <= lastEventSeq) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        streamText: `${pendingTask.value?.streamText ?? ''}${text}`,
        lastStreamEventSeq: eventSeq || lastEventSeq,
        streamEventSeq: eventSeq || lastEventSeq,
      }
      followLatestAnswerIfPinned()
    },
    sources(payload) {
      if (!isCurrentStream()) {
        return
      }
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        sessionId,
        streaming: true,
        sources: Array.isArray(payload) ? payload : [],
      }
    },
    message(payload) {
      if (!isCurrentStream()) {
        return
      }
      if (payload) {
        state.messageReceived = true
        messages.value = upsertQaMessage(messages.value, withTaskMode(payload, pendingTask.value || submission))
        pendingTask.value = {
          ...(pendingTask.value ?? submission),
          sessionId,
          streaming: true,
          streamText: '',
          lastStreamEventSeq: normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? initialEventSeq),
        }
      }
    },
    async done(payload) {
      if (!isCurrentStream()) {
        return
      }
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
      await followLatestAnswerIfPinned()
    },
    error(payloadOrError) {
      if (!isCurrentStream()) {
        return
      }
      const isNetworkError = payloadOrError instanceof Error
      if (isNetworkError) {
        fallbackToPolling('事件流连接中断，已切换为轮询。')
        return
      }
      state.terminal = true
      clearTaskStream()
      errorMessage.value = payloadOrError?.message || '问答任务执行失败'
      const errorPayload = payloadOrError && typeof payloadOrError === 'object' ? payloadOrError : {}
      pendingTask.value = {
        ...(pendingTask.value ?? submission),
        ...errorPayload,
        sessionId,
        taskStatus: pendingTask.value?.taskStatus || 'failed',
        streaming: false,
      }
    },
    close() {
      fallbackToPolling()
    },
  }, {
    signal: controller.signal,
    afterEventSeq: initialEventSeq,
  }).catch(() => {
    fallbackToPolling('事件流连接失败，已切换为轮询。')
  })
}

async function pollTask(sessionId, taskId) {
  try {
    const detail = await getQaTask(sessionId, taskId)
    const currentStreamText = pendingTask.value?.streamText ?? ''
    const partialStreamText = detail.partialResponseText ?? ''
    const currentSeq = normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? pendingTask.value?.streamEventSeq)
    const detailSeq = normalizeStreamEventSeq(detail.streamEventSeq)
    const progressEvents = mergeProgressEvents(
      pendingTask.value?.progressEvents,
      detail.progressEvents ?? detail.progress_events ?? detail.latestLogs,
    )
    pendingTask.value = {
      ...(pendingTask.value ?? {}),
      ...detail,
      sessionId,
      streamText: currentStreamText || partialStreamText,
      progressEvents,
      latestLogs: normalizeTaskLogs(detail.latestLogs ?? pendingTask.value?.latestLogs, progressEvents),
      lastStreamEventSeq: Math.max(currentSeq, detailSeq),
    }
    updateUserMessageTask(detail)

    if (!isTerminalTaskStatus(detail.taskStatus)) {
      scheduleTaskPoll(sessionId, taskId, resolvePollingDelaySeconds(detail))
      return
    }

    if (detail.taskStatus === 'success') {
      if (detail.assistantMessage) {
        messages.value = upsertQaMessage(messages.value, withTaskMode(detail.assistantMessage, detail))
      } else {
        await refreshAssistantAfterEmptySuccess(sessionId, taskId)
      }
      if (memoryScopeReady.value) {
        await loadMemoryState(selectedCourseId.value, selectedKnowledgeBaseId.value)
      }
      statusMessage.value = `回答已生成。${resolveContextStatusText(detail)}。${resolveMemoryStatusText(detail)}`
      pendingTask.value = null
      await followLatestAnswerIfPinned()
      return
    }

    const fallbackMessage = detail.taskStatus === 'stale'
      ? (detail.timeoutMessage || '任务心跳超时，请稍后重试')
      : (detail.errorMessage || '问答任务执行失败')
    errorMessage.value = fallbackMessage
    pendingTask.value = {
      ...(pendingTask.value ?? {}),
      ...detail,
      sessionId,
      streaming: false,
      streamText: pendingTask.value?.streamText || detail.partialResponseText || '',
      progressEvents,
      latestLogs: normalizeTaskLogs(detail.latestLogs ?? pendingTask.value?.latestLogs, progressEvents),
      lastStreamEventSeq: Math.max(
        normalizeStreamEventSeq(pendingTask.value?.lastStreamEventSeq ?? pendingTask.value?.streamEventSeq),
        normalizeStreamEventSeq(detail.streamEventSeq),
      ),
    }
  } catch (error) {
    errorMessage.value = error?.message || '问答任务轮询失败'
    if (pendingTask.value) {
      pendingTask.value = {
        ...pendingTask.value,
        taskStatus: pendingTask.value.taskStatus || 'failed',
        streaming: false,
      }
    }
  }
}

async function refreshAssistantAfterEmptySuccess(sessionId, taskId) {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    await sleep(700)
    const detail = await getQaTask(sessionId, taskId)
    const progressEvents = mergeProgressEvents(
      pendingTask.value?.progressEvents,
      detail.progressEvents ?? detail.progress_events ?? detail.latestLogs,
    )
    pendingTask.value = {
      ...(pendingTask.value ?? {}),
      ...detail,
      sessionId,
      progressEvents,
      latestLogs: normalizeTaskLogs(detail.latestLogs ?? pendingTask.value?.latestLogs, progressEvents),
    }
    if (detail.assistantMessage) {
      messages.value = upsertQaMessage(messages.value, withTaskMode(detail.assistantMessage, detail))
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
        ? (() => {
            const progressEvents = mergeProgressEvents(
              message.progressEvents,
              detail.progressEvents ?? detail.progress_events ?? detail.latestLogs,
            )
            return {
            ...message,
            mode: detail.mode || message.mode || '',
            taskId: detail.taskId ?? message.taskId ?? null,
            taskStatus: detail.taskStatus,
            progressStage: detail.progressStage,
            latestLogs: normalizeTaskLogs(detail.latestLogs ?? message.latestLogs, progressEvents),
            progressEvents,
            partialResponseText: detail.partialResponseText ?? message.partialResponseText ?? '',
            streamEventSeq: normalizeStreamEventSeq(detail.streamEventSeq ?? message.streamEventSeq),
          }
          })()
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

function readMainScrollMetrics() {
  const el = mainRef.value
  if (!el) {
    return null
  }
  return {
    scrollTop: el.scrollTop,
    clientHeight: el.clientHeight,
    scrollHeight: el.scrollHeight,
  }
}

function handleMainScroll() {
  const state = resolveAutoScrollAfterUserScroll(readMainScrollMetrics())
  autoScrollPinned.value = state.autoScrollPinned
  if (state.autoScrollPinned) {
    showJumpToLatest.value = false
  }
}

async function scrollToBottom(options = {}) {
  const { force = false, behavior = 'smooth' } = options
  await nextTick()
  const el = mainRef.value
  if (!el) {
    return
  }
  el.scrollTo({ top: el.scrollHeight, behavior })
  if (force || isNearScrollBottom(readMainScrollMetrics())) {
    autoScrollPinned.value = true
    showJumpToLatest.value = false
  }
}

async function followLatestAnswerIfPinned() {
  await nextTick()
  if (shouldAutoFollowNewContent({
    autoScrollPinned: autoScrollPinned.value,
    metrics: readMainScrollMetrics(),
  })) {
    await scrollToBottom({ behavior: 'auto' })
    return
  }
  showJumpToLatest.value = true
}

async function handleJumpToLatest() {
  await scrollToBottom({ force: true })
}

function autoResizeInput(event) {
  const el = event.target
  el.style.height = 'auto'
  const newHeight = Math.min(el.scrollHeight, 200)
  el.style.height = newHeight + 'px'
}

const isComposerExpanded = computed(() => {
  return input.value.includes('\n') || input.value.length > 60
})

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

function messageModeLabel(message) {
  return getModeOption(message?.mode || 'basic').shortLabel
}

function withTaskMode(message, task) {
  if (!message) {
    return message
  }
  const progressEvents = mergeProgressEvents(
    message.progressEvents ?? message.progress_events,
    task?.progressEvents ?? task?.progress_events ?? task?.latestLogs,
  )
  return {
    ...message,
    mode: message.mode || message.queryMode || message.searchMode || task?.mode || '',
    latestLogs: normalizeTaskLogs(message.latestLogs ?? task?.latestLogs, progressEvents),
    progressEvents,
  }
}

function mergeProgressEvents(currentEvents, incomingEvents) {
  return mergeRetrievalTraceEvents(
    normalizeProgressEvents(currentEvents),
    normalizeProgressEvents(incomingEvents),
  )
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
  if (type === 'graphrag_report') {
    return '报告'
  }
  if (type === 'graphrag_entity') {
    return '实体'
  }
  if (type === 'graphrag_relationship') {
    return '关系'
  }
  if (type === 'global_fallback_text_unit') {
    return '补充片段'
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
    <div ref="mainRef" class="qa-main" aria-live="polite" @scroll="handleMainScroll">
      <!-- 空态占位（让 composer 居中） -->
      <div v-if="isEmpty" class="empty-spacer"></div>

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
                {{ messageModeLabel(msg) }}
              </ModuleTag>
            </div>
            <QaRetrievalTrace
              :events="msg.progressEvents"
              :mode-label="messageModeLabel(msg)"
            />
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
            <QaRetrievalTrace
              :events="pendingProcessEvents"
              :mode-label="messageModeLabel(pendingTask)"
              :default-open="!pendingTask.streamText"
              live
            />
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
      <button
        v-if="showJumpToLatest"
        class="jump-latest"
        type="button"
        @click="handleJumpToLatest"
      >
        回到最新回答
      </button>
    </div>

    <!-- 底部 Composer -->
    <div class="composer-wrap" :class="{ centered: isEmpty }">
      <!-- 标题（仅初始态） -->
      <h1 v-if="isEmpty" class="welcome-title">从真实课程知识库开始提问</h1>

      <!-- 课程路由：候选确认面板 -->
      <div v-if="courseRouteConfirmation" class="course-route-confirmation">
        <div class="course-route-head">
          <el-icon><Search /></el-icon>
          <span>请选择要继续提问的课程</span>
          <button class="inline-action" type="button" @click="clearCourseRouteConfirmation">重新输入</button>
        </div>
        <div class="course-route-candidates">
          <button
            v-for="candidate in courseRouteConfirmation.candidates"
            :key="candidate.courseId"
            class="course-route-candidate"
            type="button"
            @click="handleCourseRouteCandidateConfirm(candidate)"
          >
            <strong>{{ candidate.name || candidate.courseId }}</strong>
            <span>{{ formatConfidence(candidate.confidence) }}</span>
            <small>{{ candidate.reason || '课程画像候选' }}</small>
          </button>
        </div>
      </div>

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
      <div v-else-if="statusMessage && !isEmpty" class="toast info">
        <el-icon><Search /></el-icon>
        <span>{{ statusMessage }}</span>
      </div>

      <div class="composer" :class="{ focused: composerFocused, expanded: isComposerExpanded }">
        <!-- 主行：＋ | textarea | 模式 | 发送（单行胶囊） -->
        <div class="composer-main-row">
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

          <textarea
            ref="composerInputRef"
            v-model="input"
            class="composer-input"
            :disabled="sending || courseRoutingLoading || restoringSession || hasActivePendingTask || Boolean(activeSessionReadOnlyMessage)"
            :placeholder="activeSessionReadOnlyMessage || '有问题，尽管问'"
            rows="1"
            @focus="composerFocused = true"
            @blur="composerFocused = false"
            @keydown.enter.exact.prevent="send"
            @input="autoResizeInput"
          ></textarea>

          <!-- 模式 chip -->
          <button class="chip mode-chip" type="button" @click="modeMenuOpen = !modeMenuOpen">
            {{ getModeOption(selectedMode).shortLabel === '智能' ? '智能' : getModeOption(selectedMode).shortLabel }} ▾
          </button>

          <!-- 学习记忆（开启后才显示） -->
          <button v-if="memoryEnabled && !isComposerExpanded" class="chip memory-chip" type="button">
            🧠 {{ learningMemoryItems.length }}
          </button>

          <!-- 发送 -->
          <button
            class="send-btn"
            :class="{ active: canSend }"
            :disabled="!canSend"
            type="button"
            aria-label="发送问题"
            @click="send"
          >
            <el-icon v-if="sending || courseRoutingLoading" class="is-loading" :size="16"><Loading /></el-icon>
            <span v-else class="send-arrow">➤</span>
          </button>
        </div>

        <!-- Popovers -->
        <div class="composer-popovers">
          <Transition name="pop">
            <div v-if="courseSelectOpen" class="scope-popover">
              <div class="scope-pop-head"><span>选择课程</span><button class="scope-pop-close" type="button" @click="courseSelectOpen = false">✕</button></div>
              <div class="scope-pop-list">
                <div class="scope-pop-item" :class="{ active: !selectedCourseId }" @click="handleCourseChange(''); courseSelectOpen = false"><span>自动识别</span><span class="scope-pop-hint">根据问题内容自动匹配</span></div>
                <div v-for="course in courses" :key="course.courseId" class="scope-pop-item" :class="{ active: selectedCourseId === course.courseId }" @click="selectedCourseId = course.courseId; handleCourseChange(course.courseId); courseSelectOpen = false"><span>{{ course.name }}</span></div>
              </div>
            </div>
          </Transition>
          <Transition name="pop">
            <div v-if="kbSelectOpen" class="scope-popover">
              <div class="scope-pop-head"><span>选择知识库</span><button class="scope-pop-close" type="button" @click="kbSelectOpen = false">✕</button></div>
              <div v-if="!selectedCourseId" class="scope-pop-empty">请先选择课程</div>
              <div v-else-if="!knowledgeBases.length" class="scope-pop-empty">该课程暂无知识库</div>
              <div v-else class="scope-pop-list">
                <div v-for="kb in knowledgeBases" :key="kb.id" class="scope-pop-item" :class="{ active: String(selectedKnowledgeBaseId) === String(kb.id), disabled: kb.activeIndexRunId == null }" @click="kb.activeIndexRunId != null && (selectedKnowledgeBaseId = String(kb.id), handleKnowledgeBaseChange(), kbSelectOpen = false)"><span>{{ kb.name }}</span><span class="scope-pop-hint">{{ kb.activeIndexRunId == null ? '未激活索引' : '索引 #' + kb.activeIndexRunId }}</span></div>
              </div>
            </div>
          </Transition>
          <Transition name="pop">
            <div v-if="modeMenuOpen" class="mode-popover">
              <div v-for="mode in QA_MODE_OPTIONS" :key="mode.value" class="mode-pop-item" :class="{ active: selectedMode === mode.value }" @click="handleModeSelect(mode.value); modeMenuOpen = false"><span class="mode-pop-label">{{ mode.label }}</span><span class="mode-pop-desc">{{ mode.description }}</span></div>
              <div class="mode-pop-foot"><label><input v-model="allowHybridSmartBeta" type="checkbox" @change="handleHybridBetaToggle" /> 允许智能推荐使用混合检索 Beta</label></div>
            </div>
          </Transition>
        </div>
      </div>

      <!-- 推荐 chips（仅初始态） -->
      <div v-if="isEmpty" class="suggest-row below-composer">
        <button class="suggest-chip" type="button" @click="input = '解释「进程」与「线程」的本质区别'">📘 解释「进程」与「线程」的本质区别</button>
        <button class="suggest-chip" type="button" @click="input = '给我整体的知识脉络'">🧭 给我整体的知识脉络</button>
        <button class="suggest-chip" type="button" @click="input = '第 3 章的例题怎么解？'">📍 第 3 章的例题怎么解？</button>
      </div>

      <div v-if="!isEmpty" class="composer-hint">
        <span><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行</span>
        <span v-if="selectedCourse">{{ selectedCourse.name }} · {{ getModeOption(modePreview.mode).shortLabel }}</span>
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
  height: calc(100vh - 64px - 48px);
  flex-direction: column;
  overflow: hidden;
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
.empty-spacer {
  flex: 1;
}

.welcome-title {
  font-family: 'Space Grotesk', inherit;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, #0f172a 20%, #7e22ce 80%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-align: center;
  margin-bottom: 32px;
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

.jump-latest {
  position: sticky;
  bottom: 104px;
  z-index: 8;
  align-self: center;
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.12);
  color: #2563eb;
  cursor: pointer;
  font-family: inherit;
  font-size: 12px;
  font-weight: 800;
  padding: 8px 14px;
}

.is-loading { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ===== Composer ===== */
.composer-wrap {
  position: sticky;
  bottom: 0;
  padding: 16px 24px 20px;
  background: linear-gradient(180deg, transparent, rgba(248, 250, 252, 0.95) 30%);
  transition: all $duration-base $ease-out;

  /* 初始态居中 */
  &.centered {
    position: absolute;
    bottom: auto;
    top: 42%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 100%;
    max-width: 720px;
    padding: 0 24px;
    background: transparent;
  }
}

.toast {
  max-width: 720px;
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

.course-route-confirmation {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(13, 148, 136, 0.24);
  border-radius: $radius-lg;
  background: #fff;
  color: #115e59;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
}

.course-route-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 800;
}

.course-route-candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
}

.course-route-candidate {
  display: grid;
  min-height: 78px;
  gap: 4px;
  padding: 10px;
  border: 1px solid rgba(13, 148, 136, 0.2);
  border-radius: $radius-md;
  background: rgba(240, 253, 250, 0.72);
  color: #0f172a;
  cursor: pointer;
  text-align: left;

  strong,
  span,
  small {
    overflow-wrap: anywhere;
  }

  span {
    color: #0f766e;
    font-size: 12px;
    font-weight: 900;
  }

  small {
    color: #475569;
    font-size: 12px;
    line-height: 1.35;
  }
}

.inline-action,
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
  max-width: 720px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 999px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.06);
  overflow: visible;
  position: relative;
  transition: border-radius $duration-base $ease-out, border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &.focused {
    border-color: rgba(147, 51, 234, 0.35);
    box-shadow: 0 4px 20px rgba(15, 23, 42, 0.08), 0 0 0 3px rgba(147, 51, 234, 0.06);
  }

  /* 多行展开态：从胶囊变为圆角矩形 */
  &.expanded {
    border-radius: 20px;
  }
}

/* 主行：单行胶囊布局 */
.composer-main-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  transition: flex-direction $duration-base $ease-out, padding $duration-base $ease-out;
}

/* 展开态：变为纵向布局（文字上，工具栏下） */
.expanded .composer-main-row {
  flex-wrap: wrap;
  align-items: flex-end;
  padding: 12px 14px 8px;
}

.expanded .composer-input {
  flex: 1 1 100%;
  order: -1;
  padding: 4px 4px 10px;
}

.composer-input {
  display: block;
  flex: 1;
  min-width: 0;
  padding: 8px 8px;
  min-height: 32px;
  max-height: 200px;
  font-size: 15px;
  line-height: 1.5;
  color: #0f172a;
  border: 0;
  outline: 0;
  background: transparent;
  resize: none;
  font-family: inherit;
  overflow-y: auto;
  transition: padding $duration-fast $ease-out;

  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: rgba(148, 163, 184, 0.3); border-radius: 3px; }
  &::-webkit-scrollbar-thumb:hover { background: rgba(148, 163, 184, 0.5); }

  &::placeholder { color: #9ca3af; }
  &:disabled { cursor: not-allowed; opacity: 0.6; }
}

/* 展开态：＋ 在左，spacer 推模式和发送到右 */
.expanded .plus-ring-wrap { order: 0; }
.expanded .mode-chip { order: 1; margin-left: auto; }
.expanded .memory-chip { order: 2; }
.expanded .send-btn { order: 3; }

.composer-popovers {
  position: relative;
}

.toolbar-spacer { flex: 1; }

/* 推荐 chips 在 composer 下方 */
.suggest-row.below-composer {
  max-width: 720px;
  margin: 12px auto 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

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
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 0;
  cursor: pointer;
  background: #d4d4d8;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-family: inherit;
  flex-shrink: 0;
  transition: background $duration-fast $ease-out, transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  /* 有内容时激活态：紫色渐变 */
  &.active {
    background: linear-gradient(135deg, #9333ea, #6366f1);
    box-shadow: 0 6px 18px rgba(147, 51, 234, 0.3);

    &:hover { transform: translateY(-1px) scale(1.04); box-shadow: 0 10px 24px rgba(147, 51, 234, 0.4); }
  }

  &:disabled { cursor: not-allowed; }
}

.send-arrow {
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  display: inline-block;
  transform: rotate(-90deg);
  transition: transform $duration-fast $ease-out;
}

.composer-hint {
  max-width: 720px;
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
