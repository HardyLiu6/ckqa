<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AnnotationSampleList from './AnnotationSampleList.vue'
import AnnotationWorkArea from './AnnotationWorkArea.vue'
import {
  listAuditSamples,
  generateAuditSet,
  updateAuditSample,
  requestAuditSampleAiSuggestions,
} from '../../../api/prompt-tune-pipeline.js'
import { apiSampleToLocal, localSampleToUpdatePayload } from './prepare-step-api.js'
import { generateEntityId, generateRelationId } from './entity-id-generator.js'

defineEmits(['back'])

const route = useRoute()

const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 }
const BUSINESS_CODE_HAS_ANNOTATED_SAMPLES = 4103

const samples = ref([])
const taskSummary = ref(null)
const tasksExpanded = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const ideOpen = ref(false)
const activeSampleId = ref('')

// 当前正在生成 AI 候选的 sampleId（null 表示无任务）。
// 选这种"单标量"形态而非 Map 是因为本期单端用户单标签页基本不会同时点多个 sample。
// 如果未来需要"多 sample 并行生成"（多标签页 / 团队协作），可以改为 ref({}) 形态：
//   aiSuggestionLoadingBySampleId.value[sampleId] = true/false
// 这是 Phase 7+ 优化方向，本期 YAGNI。
const aiSuggestionLoadingSampleId = ref(null)

// 已采纳 AI 候选实体的本地映射：originalName -> goldEntityId
// AI 关系候选用实体名引用 source/target，采纳关系时优先查这个 map 取 id
const aiEntityNameToGoldId = ref(new Map())

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

const activeSample = computed(() =>
  samples.value.find((s) => s.id === activeSampleId.value) ?? null
)

const doneCount = computed(() => samples.value.filter((s) => s.status === 'done').length)
const totalCount = computed(() => samples.value.length)
const progressPercent = computed(() =>
  totalCount.value > 0 ? Math.round((doneCount.value / totalCount.value) * 100) : 0
)

onMounted(loadOrGenerateSamples)

async function loadOrGenerateSamples() {
  if (!buildRunId.value) {
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    let apiSamples = await listAuditSamples(buildRunId.value)
    if (!Array.isArray(apiSamples) || apiSamples.length === 0) {
      ElMessage.info('正在生成校准集，约 15 秒...')
      apiSamples = await safeGenerateAuditSet({ allowForcePrompt: true })
    }
    if (Array.isArray(apiSamples)) applyApiSamples(apiSamples)
  } catch (err) {
    errorMessage.value = err?.message ?? '加载校准集失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

async function safeGenerateAuditSet({ allowForcePrompt }) {
  try {
    return await generateAuditSet(buildRunId.value, { force: false })
  } catch (err) {
    if (err?.code === BUSINESS_CODE_HAS_ANNOTATED_SAMPLES && allowForcePrompt) {
      try {
        await ElMessageBox.confirm(
          '当前构建已有人工标注，重新生成会清空当前进度。是否确认覆盖？',
          '提示',
          { confirmButtonText: '确认覆盖', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return null
      }
      return await generateAuditSet(buildRunId.value, { force: true })
    }
    throw err
  }
}

function applyApiSamples(apiSamples) {
  const localSamples = (apiSamples ?? [])
    .map(apiSampleToLocal)
    .filter(Boolean)
    .sort((a, b) =>
      (PRIORITY_ORDER[a.auditPriority] ?? 9) - (PRIORITY_ORDER[b.auditPriority] ?? 9)
    )
  samples.value = localSamples
  taskSummary.value = computeTaskSummary(localSamples)
  const initialActive =
    localSamples.find((s) => s.status === 'in_progress') ??
    localSamples.find((s) => s.status === 'not_started') ??
    localSamples[0]
  activeSampleId.value = initialActive?.id ?? ''
}

function computeTaskSummary(localSamples) {
  const buckets = { high: 0, medium: 0, low: 0 }
  for (const s of localSamples) {
    buckets[s.auditPriority] = (buckets[s.auditPriority] ?? 0) + 1
  }
  return {
    samplesBuilt: { count: localSamples.length, types: 0, durationSec: null },
    auditSampled: {
      high: buckets.high,
      medium: buckets.medium,
      low: buckets.low,
      total: localSamples.length,
      durationSec: null,
    },
  }
}

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

function findSample(sampleId) {
  return samples.value.find((s) => s.id === sampleId) ?? null
}

async function persistFields(sample, { fields = [], clearFields = [] } = {}) {
  if (!buildRunId.value || !sample) return
  const payload = localSampleToUpdatePayload(sample, { fields, clearFields })
  if (Object.keys(payload).length === 0) return
  try {
    const updated = await updateAuditSample(buildRunId.value, sample.id, payload)
    Object.assign(sample, apiSampleToLocal(updated))
  } catch (err) {
    ElMessage.error(err?.message ?? '保存失败，请重试')
    throw err
  }
}

async function handleAcceptEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const previousStatus = sample.status
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  // 注意 source 字段语义：AI 候选用 suggestionSource 标记来源；落到 goldEntities 时
  // source 字段表示"采纳来源"。剥掉 suggestionSource 保持 entity shape 干净。
  const acceptedEntity = { ...picked, source: 'accepted' }
  delete acceptedEntity.suggestionSource
  sample.goldEntities.push(acceptedEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'

  // 记录 originalName → 新 entity id 映射，供后续采纳 AI 关系候选使用
  const originalName = picked.name
  if (originalName) {
    aiEntityNameToGoldId.value.set(originalName, acceptedEntity.id)
  }

  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已采纳')
  } catch {
    sample.status = previousStatus
    sample.aiSuggestedEntities.splice(idx, 0, picked)
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== acceptedEntity.id)
    if (originalName) aiEntityNameToGoldId.value.delete(originalName)
  }
}

function handleRejectEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedEntities = sample.aiSuggestedEntities.filter((e) => e.id !== entityId)
}

async function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const removedEntity = sample.goldEntities.find((e) => e.id === entityId)
  // 级联删除：所有引用该实体的关系也一并删除
  const removedRelations = sample.goldRelations.filter(
    (r) => r.sourceEntityId === entityId || r.targetEntityId === entityId
  )
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
  if (removedRelations.length > 0) {
    sample.goldRelations = sample.goldRelations.filter(
      (r) => r.sourceEntityId !== entityId && r.targetEntityId !== entityId
    )
  }
  const fields = removedRelations.length > 0
    ? ['goldEntities', 'goldRelations']
    : ['goldEntities']
  try {
    await persistFields(sample, { fields })
    if (removedRelations.length > 0) {
      ElMessage.info(`已删除实体及其 ${removedRelations.length} 条关联关系`)
    }
  } catch {
    if (removedEntity) sample.goldEntities.push(removedEntity)
    if (removedRelations.length > 0) sample.goldRelations.push(...removedRelations)
  }
}

async function handleAcceptRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const picked = sample.aiSuggestedRelations[idx]

  // AI 候选关系用 originalSource/originalTarget（实体名字符串），需要找 entity id：
  // 1. 优先查 aiEntityNameToGoldId（用户已采纳的 AI 实体记录的 originalName→id 映射）
  // 2. fallback 按 name 查 sample.goldEntities（手动添加的实体或先于 AI 实体存在的同名实体）
  // 3. fallback 命中多个同名实体时取第一个并提示——同名同类型在 Phase 2c-pre 已经有重名警告，
  //    此场景概率低，不做严格 type 校验（GraphRAG 输出的 relation 不带 source_type/target_type）
  let sourceEntityId = picked.sourceEntityId
  let targetEntityId = picked.targetEntityId
  let ambiguousMatch = false
  if (!sourceEntityId && picked.originalSource) {
    sourceEntityId = aiEntityNameToGoldId.value.get(picked.originalSource)
    if (!sourceEntityId) {
      const matches = sample.goldEntities.filter((e) => e.name === picked.originalSource)
      if (matches.length > 1) ambiguousMatch = true
      sourceEntityId = matches[0]?.id
    }
  }
  if (!targetEntityId && picked.originalTarget) {
    targetEntityId = aiEntityNameToGoldId.value.get(picked.originalTarget)
    if (!targetEntityId) {
      const matches = sample.goldEntities.filter((e) => e.name === picked.originalTarget)
      if (matches.length > 1) ambiguousMatch = true
      targetEntityId = matches[0]?.id
    }
  }
  if (!sourceEntityId || !targetEntityId) {
    ElMessage.warning(
      `请先采纳两端实体（缺少：${!sourceEntityId ? picked.originalSource : ''}${
        !sourceEntityId && !targetEntityId ? '、' : ''
      }${!targetEntityId ? picked.originalTarget : ''}）`
    )
    return
  }
  if (ambiguousMatch) {
    ElMessage.warning('两端实体存在同名候选，已绑定第一个匹配项；如果不正确请删除后重新建立关系')
  }

  sample.aiSuggestedRelations.splice(idx, 1)
  const newRelation = {
    id: picked.id,
    sourceEntityId,
    targetEntityId,
    type: picked.type,
    evidence: picked.evidence,
    description: picked.description,
    source: 'accepted',
  }
  sample.goldRelations.push(newRelation)
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
    ElMessage.success('已采纳')
  } catch {
    sample.aiSuggestedRelations.splice(idx, 0, picked)
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
  }
}

function handleRejectRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedRelations = sample.aiSuggestedRelations.filter((r) => r.id !== relationId)
}

async function handleDeleteRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const removed = sample.goldRelations.find((r) => r.id === relationId)
  sample.goldRelations = sample.goldRelations.filter((r) => r.id !== relationId)
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
  } catch {
    if (removed) sample.goldRelations.push(removed)
  }
}

async function handleFinishSample(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  if (sample.goldEntities.length === 0) {
    ElMessage.warning('至少标注 1 个实体后才能完成；如确实无可抽取实体，请点"跳过"')
    return
  }
  const previousStatus = sample.status
  sample.status = 'done'
  try {
    await persistFields(sample, { fields: ['status'] })
    const nextSample = samples.value.find((s) => s.status === 'not_started')
    if (nextSample) {
      activeSampleId.value = nextSample.id
      ElMessage.success('已完成')
    } else {
      ElMessage.success('已完成 · 所有样本已处理完毕，可前往下一步')
    }
  } catch {
    sample.status = previousStatus
  }
}

async function handleSkipSample(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  const previousStatus = sample.status
  sample.status = 'skipped'
  try {
    await persistFields(sample, { fields: ['status'] })
    const nextSample = samples.value.find((s) => s.status === 'not_started')
    if (nextSample) {
      activeSampleId.value = nextSample.id
      ElMessage.info('已跳过')
    } else {
      ElMessage.success('已跳过 · 所有样本已处理完毕，可前往下一步')
    }
  } catch {
    sample.status = previousStatus
  }
}

function sortSuggestionsByConfidence() {
  if (!activeSample.value) return
  activeSample.value.aiSuggestedEntities.sort(
    (a, b) => (b.confidence ?? 0) - (a.confidence ?? 0)
  )
}

async function handleRequestAiSuggestions(sampleId) {
  const sample = findSample(sampleId)
  if (!sample) return
  if (aiSuggestionLoadingSampleId.value === sampleId) return  // 防重复点击同一样本
  aiSuggestionLoadingSampleId.value = sampleId
  ElMessage.info('已开始生成 AI 候选，请稍候...')
  try {
    const response = await requestAuditSampleAiSuggestions(buildRunId.value, sample.id)
    // response 形态：{ entities: [...], relations: [...] }
    // 实体：每条带 suggestionSource: 'ai_suggested'，name/type/confidence 等领域字段保留
    // 关系：每条带 suggestionSource: 'ai_suggested'、originalSource/originalTarget（实体名）、type
    //
    // 注意 unwrapApiResponse 的形态：CKQA api/client.js 在业务成功时直接返回 body.data，
    // 所以 response 就是 AiSuggestionResponse 内容（{entities, relations}），不需要再 .data
    sample.aiSuggestedEntities = (response?.entities ?? []).map((e, idx) => ({
      ...e,
      id: e.id ?? `ai_e_${Date.now()}_${idx}`,  // 临时本地 id（不持久化）
    }))
    sample.aiSuggestedRelations = (response?.relations ?? []).map((r, idx) => ({
      ...r,
      id: r.id ?? `ai_r_${Date.now()}_${idx}`,
    }))
    ElMessage.success(
      `生成完成：${sample.aiSuggestedEntities.length} 个实体、${sample.aiSuggestedRelations.length} 个关系候选`
    )
  } catch (err) {
    // axios 拦截器已经把 'timeout of Xms exceeded' 转成中文'请求超时，请稍后重试'，
    // 但 AI 候选场景需要更具体的提示文案
    const message = err?.timeout
      ? 'AI 候选生成超时（>5 分钟），请稍后重试'
      : (err?.message ?? 'AI 候选生成失败')
    ElMessage.error(message)
  } finally {
    if (aiSuggestionLoadingSampleId.value === sampleId) {
      aiSuggestionLoadingSampleId.value = null
    }
  }
}

function handleDismissReusedFrom(sampleId) {
  const sample = findSample(sampleId)
  if (sample) {
    // 仅隐藏复用提示横幅，不删除已预填的 gold 数据。
    // 用户如需删除某条预填实体/关系，使用 EntityCard/RelationCard 上的删除按钮。
    sample.reusedFrom = null
  }
}

async function handleCreateEntity(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newEntity = {
    id: generateEntityId(),
    name: payload.name,
    type: payload.type,
    description: payload.description,
    source: 'manual',
  }
  // 拖选场景：把 spanStart/spanEnd 写入 entity，原文卡才能渲染紫色高亮
  if (Number.isInteger(payload.spanStart) && Number.isInteger(payload.spanEnd)) {
    newEntity.spanStart = payload.spanStart
    newEntity.spanEnd = payload.spanEnd
  }
  sample.goldEntities.push(newEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已添加实体')
  } catch {
    sample.status = previousStatus
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== newEntity.id)
  }
}

async function handleCreateRelation(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newRelation = {
    id: generateRelationId(),
    sourceEntityId: payload.sourceEntityId,
    targetEntityId: payload.targetEntityId,
    type: payload.type,
    evidence: payload.evidence,
    source: 'manual',
  }
  sample.goldRelations.push(newRelation)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldRelations', 'status'] })
    ElMessage.success('已添加关系')
  } catch {
    sample.status = previousStatus
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
  }
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
        <div v-if="taskSummary">
          <span>02.1 调优样本集</span>
          <strong>
            {{ taskSummary.samplesBuilt.count }} 条
            <template v-if="taskSummary.samplesBuilt.types">
              · {{ taskSummary.samplesBuilt.types }} 类型
            </template>
            <template v-if="taskSummary.samplesBuilt.durationSec != null">
              · 用时 {{ taskSummary.samplesBuilt.durationSec }} 秒
            </template>
          </strong>
        </div>
        <div v-if="taskSummary">
          <span>02.2 校准集采样</span>
          <strong>
            分层抽样 {{ taskSummary.auditSampled.total }} 条 · high {{ taskSummary.auditSampled.high }} / medium {{ taskSummary.auditSampled.medium }} / low {{ taskSummary.auditSampled.low }}
            <template v-if="taskSummary.auditSampled.durationSec != null">
              · 用时 {{ taskSummary.auditSampled.durationSec }} 秒
            </template>
          </strong>
        </div>
      </div>
    </article>

    <!-- 加载 / 错误状态 -->
    <div v-if="loading" class="prepare-loading">
      <span>正在加载校准集...</span>
    </div>
    <div v-else-if="errorMessage" class="prepare-error">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="loadOrGenerateSamples">重试</el-button>
    </div>

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
            :ai-suggestion-loading-sample-id="aiSuggestionLoadingSampleId"
            @finish-sample="handleFinishSample"
            @skip-sample="handleSkipSample"
            @accept-entity="handleAcceptEntity"
            @reject-entity="handleRejectEntity"
            @delete-entity="handleDeleteEntity"
            @accept-relation="handleAcceptRelation"
            @reject-relation="handleRejectRelation"
            @delete-relation="handleDeleteRelation"
            @sort-suggestions-by-confidence="sortSuggestionsByConfidence"
            @create-entity="handleCreateEntity"
            @create-relation="handleCreateRelation"
            @request-ai-suggestions="handleRequestAiSuggestions"
            @dismiss-reused-from="handleDismissReusedFrom"
          />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.prepare-loading {
  padding: 32px;
  text-align: center;
  color: var(--ckqa-text-muted, #78716c);
}
.prepare-error {
  padding: 24px;
  text-align: center;
  color: var(--ckqa-danger, #dc2626);
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
</style>
