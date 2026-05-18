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

// 'next' 事件保留接口但本期不主动触发：当所有样本完成时由用户在 02 主页面手动点
// 「下一步」推进到 03 步（见 advanceToNextSampleOrFinish），避免 IDE 关闭瞬间被传送
const emit = defineEmits(['back', 'next'])

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
    await persistFields(sample, { fields: ['goldEntities', 'aiSuggestedEntities', 'status'] })
    ElMessage.success('已采纳')
  } catch {
    sample.status = previousStatus
    sample.aiSuggestedEntities.splice(idx, 0, picked)
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== acceptedEntity.id)
    if (originalName) aiEntityNameToGoldId.value.delete(originalName)
  }
}

async function handleRejectEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  try {
    await persistFields(sample, { fields: ['aiSuggestedEntities'] })
  } catch {
    sample.aiSuggestedEntities.splice(idx, 0, picked)
  }
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
    await persistFields(sample, { fields: ['goldRelations', 'aiSuggestedRelations'] })
    ElMessage.success('已采纳')
  } catch {
    sample.aiSuggestedRelations.splice(idx, 0, picked)
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
  }
}

async function handleRejectRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedRelations.splice(idx, 1)
  try {
    await persistFields(sample, { fields: ['aiSuggestedRelations'] })
  } catch {
    sample.aiSuggestedRelations.splice(idx, 0, picked)
  }
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
    advanceToNextSampleOrFinish('已完成')
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
    advanceToNextSampleOrFinish('已跳过', { neutral: true })
  } catch {
    sample.status = previousStatus
  }
}

/**
 * 完成 / 跳过当前样本后的统一处理：
 * - 优先切到下一条「未开始 / 进行中」的样本，让用户继续标注
 * - 当所有样本都已处于终态（done / skipped）时，关闭 IDE 回到 02 主页面，提示完成；
 *   不自动 emit('next')，让用户在主页面看到 2/2 完成总览后手动点「下一步」进 03 步，
 *   避免 IDE 关闭瞬间页面切换给人"被传送"的不连贯感
 *
 * @param {string} primaryAction - "已完成" 或 "已跳过"
 * @param {{ neutral?: boolean }} options - neutral=true 时单条切换用 info 而非 success
 */
function advanceToNextSampleOrFinish(primaryAction, { neutral = false } = {}) {
  const pending = samples.value.find(
    (s) => s.id !== activeSampleId.value && (s.status === 'not_started' || s.status === 'in_progress'),
  )
  if (pending) {
    activeSampleId.value = pending.id
    if (neutral) ElMessage.info(primaryAction)
    else ElMessage.success(primaryAction)
    return
  }
  // 全部处于终态（done / skipped）：关闭 IDE，把用户送回 02 主页面
  const allFinalized = samples.value.every((s) => s.status === 'done' || s.status === 'skipped')
  if (allFinalized) {
    closeIde()
    ElMessage.success(`${primaryAction} · 所有样本已处理完毕，请在下方点击「下一步」进入 03 步`)
  } else {
    // 兜底：理论上不会进入这里，但若存在未识别状态，至少给出非误导文案
    if (neutral) ElMessage.info(primaryAction)
    else ElMessage.success(primaryAction)
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
    // 后端已经为每个候选分配稳定 id（ai_e_<sampleId>_<idx> / ai_r_<sampleId>_<idx>）
    // 并通过 ai_suggested_* 字段持久化到 DB。前端不再覆盖 id，刷新后能通过同 id 定位候选。
    sample.aiSuggestedEntities = response?.entities ?? []
    sample.aiSuggestedRelations = response?.relations ?? []
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

// ─── 批量操作 handler ───────────────────────────────────────────────────
// 设计：批量 = 循环单条，最后做一次 persistFields 把所有变化一起写。
// 对失败处理简化：批量操作中任一条失败 → toast + reload sample 状态以保证一致

async function handleAcceptSelectedEntities(entityIds) {
  const sample = activeSample.value
  if (!sample || entityIds.length === 0) return
  const acceptedSnapshot = []
  for (const id of entityIds) {
    const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === id)
    if (idx < 0) continue
    const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
    const acceptedEntity = { ...picked, source: 'accepted' }
    delete acceptedEntity.suggestionSource
    sample.goldEntities.push(acceptedEntity)
    acceptedSnapshot.push({ picked, acceptedEntity })
    if (picked.name) {
      aiEntityNameToGoldId.value.set(picked.name, acceptedEntity.id)
    }
  }
  if (sample.status === 'not_started' && acceptedSnapshot.length > 0) sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'aiSuggestedEntities', 'status'] })
    ElMessage.success(`已批量采纳 ${acceptedSnapshot.length} 个候选实体`)
  } catch {
    // 整体回滚
    for (const { picked, acceptedEntity } of acceptedSnapshot) {
      sample.goldEntities = sample.goldEntities.filter((e) => e.id !== acceptedEntity.id)
      sample.aiSuggestedEntities.push(picked)
      if (picked.name) aiEntityNameToGoldId.value.delete(picked.name)
    }
  }
}

async function handleRejectSelectedEntities(entityIds) {
  const sample = activeSample.value
  if (!sample || entityIds.length === 0) return
  const removed = []
  for (const id of entityIds) {
    const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === id)
    if (idx < 0) continue
    const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
    removed.push({ idx, picked })
  }
  try {
    await persistFields(sample, { fields: ['aiSuggestedEntities'] })
    ElMessage.success(`已批量拒绝 ${removed.length} 个候选实体`)
  } catch {
    for (const { idx, picked } of removed) {
      sample.aiSuggestedEntities.splice(idx, 0, picked)
    }
  }
}

async function handleAcceptSelectedRelations(relationIds) {
  const sample = activeSample.value
  if (!sample || relationIds.length === 0) return
  const accepted = []
  const skipped = []
  for (const id of relationIds) {
    const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === id)
    if (idx < 0) continue
    const picked = sample.aiSuggestedRelations[idx]

    // 沿用单条 accept 的实体 id 解析逻辑
    let sourceEntityId = picked.sourceEntityId
    let targetEntityId = picked.targetEntityId
    if (!sourceEntityId && picked.originalSource) {
      sourceEntityId = aiEntityNameToGoldId.value.get(picked.originalSource)
        ?? sample.goldEntities.find((e) => e.name === picked.originalSource)?.id
    }
    if (!targetEntityId && picked.originalTarget) {
      targetEntityId = aiEntityNameToGoldId.value.get(picked.originalTarget)
        ?? sample.goldEntities.find((e) => e.name === picked.originalTarget)?.id
    }
    if (!sourceEntityId || !targetEntityId) {
      skipped.push(picked)
      continue
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
    accepted.push({ picked, newRelation })
  }
  try {
    if (accepted.length > 0) {
      await persistFields(sample, { fields: ['goldRelations', 'aiSuggestedRelations'] })
    }
    const msg = [
      accepted.length > 0 ? `已批量采纳 ${accepted.length} 个候选关系` : '',
      skipped.length > 0 ? `${skipped.length} 个因两端实体未采纳被跳过` : '',
    ].filter(Boolean).join('，')
    if (accepted.length > 0 && skipped.length === 0) ElMessage.success(msg)
    else if (skipped.length > 0) ElMessage.warning(msg)
  } catch {
    for (const { picked, newRelation } of accepted) {
      sample.aiSuggestedRelations.push(picked)
      sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
    }
  }
}

async function handleRejectSelectedRelations(relationIds) {
  const sample = activeSample.value
  if (!sample || relationIds.length === 0) return
  const removed = []
  for (const id of relationIds) {
    const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === id)
    if (idx < 0) continue
    const [picked] = sample.aiSuggestedRelations.splice(idx, 1)
    removed.push({ idx, picked })
  }
  try {
    await persistFields(sample, { fields: ['aiSuggestedRelations'] })
    ElMessage.success(`已批量拒绝 ${removed.length} 个候选关系`)
  } catch {
    for (const { idx, picked } of removed) {
      sample.aiSuggestedRelations.splice(idx, 0, picked)
    }
  }
}

async function handleDeleteSelectedEntities(entityIds) {
  const sample = activeSample.value
  if (!sample || entityIds.length === 0) return
  // 级联收集：被删实体引用的关系
  const removedEntities = sample.goldEntities.filter((e) => entityIds.includes(e.id))
  const removedRelations = sample.goldRelations.filter(
    (r) => entityIds.includes(r.sourceEntityId) || entityIds.includes(r.targetEntityId)
  )
  sample.goldEntities = sample.goldEntities.filter((e) => !entityIds.includes(e.id))
  sample.goldRelations = sample.goldRelations.filter(
    (r) => !entityIds.includes(r.sourceEntityId) && !entityIds.includes(r.targetEntityId)
  )
  const fields = removedRelations.length > 0 ? ['goldEntities', 'goldRelations'] : ['goldEntities']
  try {
    await persistFields(sample, { fields })
    const cascade = removedRelations.length > 0 ? `，及关联 ${removedRelations.length} 条关系` : ''
    ElMessage.success(`已批量删除 ${removedEntities.length} 个实体${cascade}`)
  } catch {
    sample.goldEntities.push(...removedEntities)
    if (removedRelations.length > 0) sample.goldRelations.push(...removedRelations)
  }
}

async function handleDeleteSelectedRelations(relationIds) {
  const sample = activeSample.value
  if (!sample || relationIds.length === 0) return
  const removed = sample.goldRelations.filter((r) => relationIds.includes(r.id))
  sample.goldRelations = sample.goldRelations.filter((r) => !relationIds.includes(r.id))
  try {
    await persistFields(sample, { fields: ['goldRelations'] })
    ElMessage.success(`已批量删除 ${removed.length} 条关系`)
  } catch {
    sample.goldRelations.push(...removed)
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
            @accept-selected-entities="handleAcceptSelectedEntities"
            @reject-selected-entities="handleRejectSelectedEntities"
            @accept-selected-relations="handleAcceptSelectedRelations"
            @reject-selected-relations="handleRejectSelectedRelations"
            @delete-selected-entities="handleDeleteSelectedEntities"
            @delete-selected-relations="handleDeleteSelectedRelations"
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
