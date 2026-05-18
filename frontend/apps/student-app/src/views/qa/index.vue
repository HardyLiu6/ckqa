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
  deleteQaFeedback,
  getQaSession,
  getQaTask,
  listQaMessages,
  sendQaMessage,
  submitQaFeedback,
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
} from './qa-mode-model'
import QaMarkdownContent from './QaMarkdownContent.vue'
import {
  isTerminalTaskStatus,
  hasActiveIndexChanged,
  isArchivedReadOnlySession,
  isLegacyReadOnlySession,
  matchCourseForQuestion,
  normalizeCourseList,
  normalizeKnowledgeBaseList,
  normalizeQaFeedback,
  normalizeQaMessage,
  normalizeQaSession,
  resolveSessionLifecycleStatusText,
  resolveContextStatusText,
  resolvePollingDelaySeconds,
  selectReadyKnowledgeBase,
  upsertQaMessage,
} from './qa-session-model'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const mainRef = ref(null)

const courses = ref([])
const knowledgeBases = ref([])
const messages = ref([])
const selectedCourseId = ref('')
const selectedKnowledgeBaseId = ref('')
const selectedMode = ref(SMART_QA_MODE)
const allowHybridSmartBeta = ref(false)
const input = ref(typeof route.query.topic === 'string' ? route.query.topic : '')
const activeSession = ref(null)
const pendingTask = ref(null)
const hybridWarmupStatus = ref('idle')
const hybridWarmupMessage = ref('')
const loadingCourses = ref(false)
const loadingKnowledgeBases = ref(false)
const restoringSession = ref(false)
const sending = ref(false)
const feedbackSubmittingMessageId = ref(null)
const errorMessage = ref('')
const statusMessage = ref('')
let pollTimer = null

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
const hybridWarmupText = computed(() => {
  if (hybridWarmupStatus.value === 'warming') {
    return '混合检索准备中'
  }
  if (hybridWarmupStatus.value === 'ready') {
    return '混合检索已就绪'
  }
  if (hybridWarmupStatus.value === 'fallback') {
    return '混合检索降级可用'
  }
  if (hybridWarmupStatus.value === 'not_ready') {
    return '混合检索未就绪'
  }
  return ''
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

async function loadKnowledgeBases(courseId) {
  if (!courseId) {
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
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
    return nextKnowledgeBases
  } catch (error) {
    errorMessage.value = error?.message || '知识库列表加载失败'
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = ''
    return []
  } finally {
    loadingKnowledgeBases.value = false
  }
}

async function handleCourseChange(courseId) {
  resetConversation()
  await clearSessionQuery()
  selectedKnowledgeBaseId.value = ''
  await loadKnowledgeBases(courseId)
}

async function handleKnowledgeBaseChange() {
  resetConversation()
  await clearSessionQuery()
}

function resetConversation() {
  clearPollTimer()
  activeSession.value = null
  pendingTask.value = null
  messages.value = []
  hybridWarmupStatus.value = 'idle'
  hybridWarmupMessage.value = ''
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

    const modeResolution = resolveQaMode(text, selectedMode.value, qaModeResolveOptions())
    if (modeResolution.mode === 'hybrid_v0') {
      await ensureHybridWarmup(course, knowledgeBase)
    }
    const session = await ensureSession(course, knowledgeBase, text)
    const submission = await sendQaMessage(session.id, {
      mode: modeResolution.mode,
      content: text,
    })

    messages.value = upsertQaMessage(messages.value, submission.userMessage)
    pendingTask.value = {
      ...submission,
      sessionId: session.id,
      mode: modeResolution.mode,
      routeReason: modeResolution.reason,
      queryText: text,
    }
    statusMessage.value = modeResolution.fromSmart
      ? `智能推荐为 ${modeResolution.mode} 模式：${modeResolution.reason}。${resolveContextStatusText(submission)}`
      : `已使用 ${modeResolution.mode} 模式提交问题。${resolveContextStatusText(submission)}`
    input.value = ''
    await scrollToBottom()
    scheduleTaskPoll(session.id, submission.taskId, resolvePollingDelaySeconds(submission))
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

async function ensureHybridWarmup(course, knowledgeBase) {
  if (!course?.courseId || !knowledgeBase?.id) {
    return
  }
  if (hybridWarmupStatus.value === 'ready' || hybridWarmupStatus.value === 'warming') {
    return
  }
  hybridWarmupStatus.value = 'warming'
  hybridWarmupMessage.value = '正在预热本地混合检索索引'
  try {
    const result = await warmupHybrid({
      courseId: course.courseId,
      knowledgeBaseId: knowledgeBase.id,
    })
    hybridWarmupStatus.value = result?.ready ? 'ready' : 'not_ready'
    hybridWarmupMessage.value = result?.message || (result?.ready ? '混合检索已就绪' : '混合检索准备未完成，可继续降级尝试')
  } catch (error) {
    hybridWarmupStatus.value = 'fallback'
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
      await loadKnowledgeBases(session.courseId)
    }
    selectedKnowledgeBaseId.value = session.knowledgeBaseId != null ? String(session.knowledgeBaseId) : ''
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
      statusMessage.value = '回答已生成'
      if (detail.contextStrategy) {
        statusMessage.value = `回答已生成。${resolveContextStatusText(detail)}`
      }
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
    <section class="qa-control-panel" aria-label="问答设置">
      <div class="qa-scope-grid">
        <label class="scope-field">
          <span>课程</span>
          <el-select
            v-model="selectedCourseId"
            class="scope-select"
            clearable
            filterable
            :loading="loadingCourses"
            placeholder="自动识别或选择课程"
            @change="handleCourseChange"
          >
            <el-option
              v-for="course in courses"
              :key="course.courseId"
              :label="course.name"
              :value="course.courseId"
            />
          </el-select>
        </label>

        <label class="scope-field">
          <span>知识库</span>
          <el-select
            v-model="selectedKnowledgeBaseId"
            class="scope-select"
            clearable
            filterable
            :disabled="!selectedCourseId"
            :loading="loadingKnowledgeBases"
            placeholder="选择可用知识库"
            @change="handleKnowledgeBaseChange"
          >
            <el-option
              v-for="knowledgeBase in knowledgeBases"
              :key="knowledgeBase.id"
              :disabled="knowledgeBase.activeIndexRunId == null"
              :label="knowledgeBase.name"
              :value="String(knowledgeBase.id)"
            >
              <span>{{ knowledgeBase.name }}</span>
              <span class="kb-option-state">
                {{ knowledgeBase.activeIndexRunId == null ? '未激活索引' : `索引 #${knowledgeBase.activeIndexRunId}` }}
              </span>
            </el-option>
          </el-select>
        </label>

        <div class="scope-actions">
          <button class="icon-action" type="button" :disabled="loadingCourses" @click="refreshScope">
            <el-icon :size="16"><Refresh /></el-icon>
            <span>刷新</span>
          </button>
        </div>
      </div>

      <div class="mode-selector" role="radiogroup" aria-label="问答模式">
        <button
          v-for="mode in QA_MODE_OPTIONS"
          :key="mode.value"
          class="mode-button"
          :class="{ active: selectedMode === mode.value }"
          type="button"
          role="radio"
          :aria-checked="selectedMode === mode.value"
          @click="handleModeSelect(mode.value)"
        >
          <span class="mode-label">{{ mode.label }}</span>
          <span class="mode-desc">{{ mode.description }}</span>
        </button>
      </div>

      <label class="beta-toggle">
        <input
          v-model="allowHybridSmartBeta"
          type="checkbox"
          @change="handleHybridBetaToggle"
        />
        <span>允许智能推荐使用混合检索 Beta</span>
      </label>

      <div class="scope-status">
        <span class="scope-pill">
          <el-icon><Reading /></el-icon>
          {{ selectedScopeLabel }}
        </span>
        <span class="scope-pill mode-pill">
          <el-icon><Connection /></el-icon>
          {{ getModeOption(selectedMode).shortLabel }} -> {{ activeModeOption.shortLabel }}
        </span>
        <span v-if="readyKnowledgeBases.length" class="scope-pill ready-pill">
          <el-icon><CircleCheck /></el-icon>
          {{ readyKnowledgeBases.length }} 个可用知识库
        </span>
        <span v-if="activeSession?.indexRunId" class="scope-pill">
          会话索引 #{{ activeSession.indexRunId }}
        </span>
        <span v-if="hybridWarmupText" class="scope-pill hybrid-warmup-pill" :title="hybridWarmupMessage">
          {{ hybridWarmupText }}
        </span>
      </div>

      <div v-if="activeIndexChanged" class="qa-alert info-alert">
        <el-icon><WarningFilled /></el-icon>
        <span>当前知识库已有新索引；本会话会继续使用旧索引 #{{ activeSession.indexRunId }}。</span>
        <button class="inline-action" type="button" @click="startNewIndexedSession">新建新索引会话</button>
      </div>
    </section>

    <div ref="mainRef" class="qa-main" aria-live="polite">
      <div v-if="isEmpty" class="empty-state">
        <div class="empty-icon">
          <el-icon :size="36"><ChatDotRound /></el-icon>
        </div>
        <div class="empty-title">从真实课程知识库开始提问</div>
        <div class="empty-desc">可以先写问题，系统会尝试识别课程；识别不明确时再手动选择。</div>
      </div>

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
                  <div v-if="sourceMeta(source)" class="source-card-meta">
                    {{ sourceMeta(source) }}
                  </div>
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

        <div v-if="pendingTask" class="msg-row role-assistant">
          <GlassCard tier="base" padding="md" class="ai-bubble pending-bubble">
            <div class="pending-head">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>{{ taskStatusText(pendingTask) }}</span>
            </div>
            <div class="pending-copy">
              {{ pendingTask.routeReason || pendingTask.timeoutMessage || '后端正在执行 GraphRAG 查询任务。' }}
            </div>
            <div class="msg-meta">
              <el-icon><Clock /></el-icon>
              <span>模式 {{ pendingTask.mode }}，轮询间隔 {{ resolvePollingDelaySeconds(pendingTask) }} 秒</span>
            </div>
          </GlassCard>
        </div>
      </template>
    </div>

    <div class="qa-input-wrap">
      <div v-if="errorMessage" class="qa-alert error-alert" role="alert">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ errorMessage }}</span>
      </div>
      <div v-else-if="activeSessionReadOnlyMessage" class="qa-alert error-alert" role="alert">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ activeSessionReadOnlyMessage }}</span>
        <button
          v-if="isArchivedReadOnlySession(activeSession)"
          class="inline-action"
          type="button"
          @click="restoreActiveSession"
        >
          恢复会话
        </button>
        <button v-else class="inline-action" type="button" @click="startNewIndexedSession">新建会话</button>
      </div>
      <div v-else-if="statusMessage" class="qa-alert info-alert">
        <el-icon><Search /></el-icon>
        <span>{{ statusMessage }}</span>
      </div>

      <GlassCard tier="base" padding="none" class="qa-input-card">
        <input
          v-model="input"
          class="qa-input"
          :disabled="sending || restoringSession || Boolean(pendingTask) || Boolean(activeSessionReadOnlyMessage)"
          :placeholder="activeSessionReadOnlyMessage || '输入课程问题，或点上方手动选择课程与模式'"
          @keyup.enter="send"
        />
        <button class="qa-send" :disabled="!canSend" type="button" aria-label="发送问题" @click="send">
          <el-icon v-if="sending" class="is-loading" :size="18"><Loading /></el-icon>
          <el-icon v-else :size="18"><Position /></el-icon>
        </button>
      </GlassCard>
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
  --qa-blue: #2563eb;
  --qa-border: rgba(148, 163, 184, 0.24);

  position: relative;
  display: flex;
  min-height: calc(100vh - 64px);
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background:
    linear-gradient(180deg, rgba(250, 245, 255, 0.92), rgba(248, 250, 252, 0.96)),
    repeating-linear-gradient(90deg, rgba(147, 51, 234, 0.05) 0 1px, transparent 1px 44px);
}

.qa-control-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--qa-border);
  border-radius: $radius-xl;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 18px 46px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
}

.icon-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(147, 51, 234, 0.24);
  border-radius: $radius-lg;
  background: #fff;
  color: var(--qa-primary-strong);
  cursor: pointer;
  font-weight: 700;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 12px 28px rgba(147, 51, 234, 0.16);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
}

.qa-scope-grid {
  display: grid;
  grid-template-columns: minmax(180px, 260px) minmax(180px, 260px) max-content;
  align-items: end;
  justify-content: start;
  gap: 10px;
}

.scope-field {
  display: grid;
  gap: 6px;

  > span {
    color: #475569;
    font-size: 12px;
    font-weight: 800;
  }
}

.scope-select {
  width: 100%;
}

.scope-actions {
  display: flex;
  align-items: flex-end;
}

.kb-option-state {
  float: right;
  color: #94a3b8;
  font-size: 12px;
}

.mode-selector {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.mode-button {
  display: grid;
  min-height: 72px;
  gap: 4px;
  padding: 10px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.76);
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out, transform $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    border-color: rgba(147, 51, 234, 0.32);
  }

  &.active {
    border-color: rgba(147, 51, 234, 0.62);
    background: linear-gradient(180deg, rgba(250, 245, 255, 0.96), rgba(255, 255, 255, 0.88));
    box-shadow: inset 0 0 0 1px rgba(147, 51, 234, 0.16), 0 12px 28px rgba(147, 51, 234, 0.12);
  }
}

.mode-label {
  color: #111827;
  font-size: 13px;
  font-weight: 900;
}

.mode-desc {
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.beta-toggle {
  display: inline-flex;
  width: max-content;
  align-items: center;
  gap: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.beta-toggle input {
  width: 15px;
  height: 15px;
  accent-color: var(--qa-primary);
}

.scope-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scope-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 999px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.mode-pill {
  color: var(--qa-primary-strong);
}

.ready-pill {
  color: var(--qa-teal);
}

.hybrid-warmup-pill {
  color: #7c3aed;
}

.qa-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 4px;
  scroll-behavior: smooth;
}

.empty-state {
  display: grid;
  min-height: 280px;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: #334155;
  text-align: center;
}

.empty-icon {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border-radius: 50%;
  background: rgba(147, 51, 234, 0.1);
  color: var(--qa-primary);
}

.empty-title {
  color: #0f172a;
  font-size: 20px;
  font-weight: 900;
}

.empty-desc {
  max-width: 420px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.msg-row {
  display: flex;
}

.role-user {
  justify-content: flex-end;
}

.role-assistant {
  justify-content: flex-start;
}

.user-bubble {
  max-width: min(680px, 76%);
  padding: 11px 14px;
  border-radius: $radius-xl $radius-xl 3px $radius-xl;
  background: linear-gradient(135deg, var(--qa-primary), #a855f7);
  box-shadow: 0 12px 28px rgba(147, 51, 234, 0.22);
  color: #fff;
}

.ai-bubble {
  max-width: min(760px, 82%);
  border-color: rgba(147, 51, 234, 0.24) !important;
  border-radius: $radius-xl $radius-xl $radius-xl 3px !important;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

.msg-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.msg-text {
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.source-cards {
  margin-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  padding-top: 10px;
}

.source-cards summary {
  width: max-content;
  cursor: pointer;
  color: #475569;
  font-size: 12px;
  font-weight: 900;
}

.source-list {
  display: grid;
  gap: 8px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.source-card {
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: $radius-md;
  background: rgba(248, 250, 252, 0.82);
  padding: 9px 10px;
}

.source-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f2937;
  font-size: 12px;
}

.source-card-head strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.source-rank {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.13);
  padding: 2px 7px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 900;
}

.source-type {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgba(147, 51, 234, 0.1);
  padding: 2px 7px;
  color: #7e22ce;
  font-size: 11px;
  font-weight: 900;
}

.source-card-meta {
  margin-top: 5px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.55;
}

.source-snippet {
  margin: 7px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.message-feedback {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.feedback-action {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  transition: border-color $duration-fast $ease-out, color $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover:not(:disabled) {
    border-color: rgba(147, 51, 234, 0.42);
    color: var(--qa-primary-strong);
  }

  &.active {
    border-color: rgba(20, 184, 166, 0.42);
    background: rgba(20, 184, 166, 0.12);
    color: #0f766e;
  }

  &:disabled {
    cursor: wait;
    opacity: 0.6;
  }
}

.msg-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 11px;
}

.user-bubble .msg-meta {
  color: rgba(255, 255, 255, 0.82);
}

.pending-bubble {
  border-color: rgba(13, 148, 136, 0.28) !important;
}

.pending-head {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--qa-teal);
  font-size: 13px;
  font-weight: 900;
}

.pending-copy {
  margin-top: 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.qa-input-wrap {
  position: sticky;
  bottom: 12px;
  display: grid;
  gap: 8px;
}

.qa-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: $radius-lg;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
}

.inline-action {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.error-alert {
  border: 1px solid rgba(239, 68, 68, 0.22);
  color: #b91c1c;
}

.info-alert {
  border: 1px solid rgba(37, 99, 235, 0.2);
  color: #1d4ed8;
}

.qa-input-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 16px !important;
  border-color: rgba(147, 51, 234, 0.34) !important;
  box-shadow: 0 0 0 4px rgba(147, 51, 234, 0.08), 0 12px 28px rgba(15, 23, 42, 0.12);
}

.qa-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #0f172a;
  font-family: inherit;
  font-size: 14px;

  &::placeholder {
    color: #9ca3af;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.qa-send {
  display: flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, var(--qa-primary), #3b82f6);
  box-shadow: 0 10px 22px rgba(147, 51, 234, 0.28);
  color: #fff;
  cursor: pointer;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 14px 28px rgba(147, 51, 234, 0.36);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.42;
  }
}

.is-loading {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 980px) {
  .qa-scope-grid,
  .mode-selector {
    grid-template-columns: 1fr;
  }

  .scope-actions,
  .icon-action {
    width: 100%;
  }

  .icon-action {
    justify-content: center;
  }

  .mode-button {
    min-height: auto;
  }

  .user-bubble,
  .ai-bubble {
    max-width: 100%;
  }
}
</style>
