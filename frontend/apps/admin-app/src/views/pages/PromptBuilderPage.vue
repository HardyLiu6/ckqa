<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import PromptBuilderSeedStep from './prompt-builder/PromptBuilderSeedStep.vue'
import PromptBuilderPlaceholderStep from './prompt-builder/PromptBuilderPlaceholderStep.vue'
import PromptBuilderPrepareStep from './prompt-builder/PromptBuilderPrepareStep.vue'
import PromptBuilderCandidatesStep from './prompt-builder/PromptBuilderCandidatesStep.vue'
import PromptBuilderScoringStep from './prompt-builder/PromptBuilderScoringStep.vue'
import PromptBuilderSaveStep from './prompt-builder/PromptBuilderSaveStep.vue'
import {
  BUILDER_STEPS,
  BUILDER_STEP_KEYS,
  resolveActiveStepKey,
  resolveNextStepKey,
  resolvePrevStepKey,
  isStepUnlocked,
} from './prompt-builder/builder-step-model.js'
import {
  MOCK_COURSE_NAME,
} from './prompt-builder/mocks/index.js'
import PromptBuilderHistoryDraftDrawer from './prompt-builder/PromptBuilderHistoryDraftDrawer.vue'

import { getBuildRun, saveBuildRunCustomPromptDraft } from '../../api/knowledge-bases.js'
import {
  getSeedAvailability,
  listPromptDrafts,
  finalizePrompt,
} from '../../api/prompt-tune-pipeline.js'

const route = useRoute()
const router = useRouter()

const buildRunId = computed(() => {
  const raw = Array.isArray(route.query.buildRunId) ? route.query.buildRunId[0] : route.query.buildRunId
  return raw ?? ''
})
const kbId = computed(() => String(route.params.kbId ?? ''))

const loading = ref(true)
const error = ref(null)

const seed = ref(null)
const seedAvailability = ref(null)  // Phase 4.5：3 个种子选项的可用状态
const historyDrafts = ref([])
const historyDraftDrawerOpen = ref(false)
const historyDraftId = ref(null)
const courseName = ref(MOCK_COURSE_NAME)

const dirty = ref(false)
const saving = ref(false)
const saveError = ref('')
const selectedCandidateId = ref('')
const saveDraftName = ref('')
const saveDraftNameTouched = ref(false)
const saveDraftDescription = ref('')
const saveDraftDescriptionTouched = ref(false)
const saveMode = ref('build_run_with_history')

const activeStepKey = computed(() => resolveActiveStepKey(route.query))

// 是否存在切换种子会丢失的下游进度。
// Phase 1a：02/03/04 是占位、05 是 mock 保存，没有真实可丢失的进度，恒为 false。
// Phase 1b/1c/1d 接入真实标注 / 候选 / 评分后，把对应状态接进来即可触发确认弹窗。
const hasDownstreamProgress = computed(() => false)

const stepStatuses = computed(() => {
  const idx = BUILDER_STEP_KEYS.indexOf(activeStepKey.value)
  return BUILDER_STEPS.map((step, i) => ({
    ...step,
    status: i < idx
      ? 'done'
      : i === idx
        ? 'ready'
        : (isStepUnlocked(step.key, { seed: seed.value }) ? 'ready' : 'blocked'),
  }))
})

onMounted(async () => {
  if (!buildRunId.value) {
    error.value = { message: '缺少构建运行上下文，请回到构建向导重新进入' }
    loading.value = false
    return
  }
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const draft = meta.customPromptDraft
    if (draft?.seed) seed.value = draft.seed

    // Phase 4.5：拉种子可用性，01 步据此决定哪些选项可点
    try {
      seedAvailability.value = await getSeedAvailability(buildRunId.value)
    } catch (availErr) {
      // 不阻塞主流程：拉失败时所有种子按"未知"处理，前端给保守回退
      // eslint-disable-next-line no-console
      console.warn('[seed-availability] 加载失败', availErr)
    }

    // Phase 6：拉历史草稿列表，01 步 history_draft 卡片据此决定 disabled / 数量角标
    await loadHistoryDrafts()

    dirty.value = false
  } catch (e) {
    error.value = { message: e?.message ?? '加载草稿失败' }
  } finally {
    loading.value = false
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async (to, from, next) => {
  if (!dirty.value) return next()
  try {
    await ElMessageBox.confirm('有未保存的修改，确定离开吗？', '离开页面',
      { type: 'warning', confirmButtonText: '离开', cancelButtonText: '继续编辑' })
    next()
  } catch {
    next(false)
  }
})

async function handleEnterScoring(selectedCandidateIds) {
  const ids = Array.isArray(selectedCandidateIds) ? selectedCandidateIds : []
  // 写到 URL，让 04 步能从 query 读取；逗号分隔便于人读
  await router.replace({
    query: {
      ...route.query,
      step: 'scoring',
      selectedCandidates: ids.join(','),
    },
  })
}

async function gotoStep(stepKey) {
  if (!BUILDER_STEP_KEYS.includes(stepKey)) return
  if (!isStepUnlocked(stepKey, { seed: seed.value })) {
    ElMessage.warning('请先在 01 步选择起始模板')
    return
  }
  if (route.query.step === stepKey) return
  await router.replace({ query: { ...route.query, step: stepKey } })
}

function gotoNext() {
  const next = resolveNextStepKey(activeStepKey.value)
  if (next) gotoStep(next)
}

function gotoPrev() {
  const prev = resolvePrevStepKey(activeStepKey.value)
  if (prev) gotoStep(prev)
}

function markDirty() { dirty.value = true }

async function persistSeedToBuildRun(seedKey) {
  // Phase 4.5：把 seed 持久化到 build run metadata.customPromptDraft.seed，
  // 让 03 步后端拿到正确的种子用于决定 baseOverride。
  // 仅写 seed 子字段，不影响 prompts.extract_graph.content。
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const nextDraft = { ...(meta.customPromptDraft ?? {}), seed: seedKey }
    await saveBuildRunCustomPromptDraft(buildRunId.value, nextDraft)
  } catch (err) {
    // eslint-disable-next-line no-console
    console.warn('[seed] 持久化失败，将由 05 步保存补救', err)
  }
}

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    if (historyDrafts.value.length === 0) {
      ElMessage.warning('本知识库暂无历史草稿，请先在 05 步保存并入库')
      return
    }
    if (historyDrafts.value.length === 1) {
      loadHistoryDraft(historyDrafts.value[0])
      return
    }
    // ≥2 条 → 打开抽屉让用户选
    historyDraftDrawerOpen.value = true
    return
  }
  if (seed.value && seed.value !== seedKey && hasDownstreamProgress.value) {
    ElMessageBox.confirm('切换种子会重置后续步骤已有的进度，确定吗？', '切换种子', { type: 'warning' })
      .then(() => {
        seed.value = seedKey
        dirty.value = true
      })
      .catch(() => {})
    return
  }
  // Phase 4.5：种子切换 → 提示当前候选已失效（不清空数据；下次进 03 步会触发覆盖式重生成）
  const seedSwitched = seed.value && seed.value !== seedKey
  seed.value = seedKey
  dirty.value = true
  persistSeedToBuildRun(seedKey)
  if (seedSwitched) {
    ElMessage.info('种子已切换，当前候选将失效，需要重新生成')
  }
}

/**
 * Phase 6：拉本知识库的历史草稿列表，刷新 01 步种子卡可用性 + 抽屉数据。
 * 失败时不阻塞主流程；列表清空，用户回退到走完整 02→03→04→05 链路。
 */
async function loadHistoryDrafts() {
  if (!kbId.value) return
  try {
    historyDrafts.value = await listPromptDrafts(kbId.value)
  } catch (err) {
    historyDrafts.value = []
    // eslint-disable-next-line no-console
    console.warn('[prompt-builder] 加载历史草稿失败', err)
  }
}

/**
 * Phase 6：选定一条历史草稿后，仅注入 seed=history_draft + historyDraftId，
 * 主动清空旧 selectedCandidateId / 03 / 04 步状态——
 * 与 spec § "history_draft 仅是种子来源、不复用旧候选 ID" 约束一致：
 * 用户后续仍需走完整 02→03→04→05 链路，finalize 走新候选。
 */
async function loadHistoryDraft(draft) {
  seed.value = 'history_draft'
  historyDraftId.value = draft.id
  selectedCandidateId.value = ''
  // 可选预填保存表单：name / description（用户后续可在 05 步覆盖）
  saveDraftName.value = draft.name
  saveDraftDescription.value = draft.description ?? ''
  saveDraftNameTouched.value = true
  saveDraftDescriptionTouched.value = !!draft.description
  dirty.value = true
  try {
    await persistSeedToBuildRun('history_draft')
    ElMessage.success(`已加载历史草稿「${draft.name}」`)
  } catch (err) {
    ElMessage.error(err?.message ?? '加载历史草稿失败')
  }
}

async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    const saveAsDraft = payload.saveMode === 'build_run_with_history'
    await finalizePrompt(buildRunId.value, {
      candidateId: payload.selectedCandidate ?? selectedCandidateId.value,
      saveAsDraft,
      draftName: payload.name ?? saveDraftName.value,
      draftDescription: payload.description ?? saveDraftDescription.value,
    })
    // finalize 成功后刷新历史草稿，保证 01 步下次进入时数量准确
    if (saveAsDraft) {
      await loadHistoryDrafts()
    }
    dirty.value = false
    if (saveAsDraft) {
      ElMessage.success('已保存到本次构建并入库历史草稿')
    } else {
      ElMessage.success('已保存到本次构建')
    }
    await router.push({
      name: 'knowledge-base-build',
      params: { kbId: kbId.value },
      query: {
        buildRunId: buildRunId.value,
        step: 'prompt',
        promptStrategy: 'custom_pipeline',
      },
    })
  } catch (e) {
    saveError.value = e?.message ?? '保存失败，请重试'
    ElMessage.error(saveError.value)
  } finally {
    saving.value = false
  }
}

function returnToWizard() {
  router.push({
    name: 'knowledge-base-build',
    params: { kbId: kbId.value },
    query: { buildRunId: buildRunId.value, step: 'prompt' },
  })
}
</script>

<template>
  <section class="prompt-builder-page">
    <header class="prompt-builder-page__header">
      <div>
        <h2>手动调优提示词</h2>
        <p v-if="buildRunId">为本次构建（构建运行 ID：{{ buildRunId }}）设计提示词。</p>
      </div>
      <el-button class="ckqa-el-button ckqa-el-button--ghost" @click="returnToWizard">
        <ChevronLeft :size="16" aria-hidden="true" />
        返回构建向导
      </el-button>
    </header>

    <RetryPanel v-if="error" :error="error" @retry="returnToWizard" />

    <div v-else-if="loading" class="prompt-builder-page__loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else>
      <WorkflowStepper
        :active-key="activeStepKey"
        :steps="stepStatuses"
        @update:active-key="gotoStep"
      />

      <div class="prompt-builder-page__body">
        <PromptBuilderSeedStep
          v-if="activeStepKey === 'seed'"
          :seed="seed"
          :seed-availability="seedAvailability"
          :history-drafts="historyDrafts"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderPrepareStep
          v-else-if="activeStepKey === 'prepare'"
          @back="gotoPrev"
        />
        <PromptBuilderCandidatesStep
          v-else-if="activeStepKey === 'candidates'"
          :dirty="dirty"
          :current-build-run-seed="seed"
          @start-scoring="handleEnterScoring"
          @back="gotoPrev"
        />
        <PromptBuilderScoringStep
          v-else-if="activeStepKey === 'scoring'"
          :dirty="dirty"
          @enter-save="(candidateId) => { selectedCandidateId = candidateId; gotoStep('save') }"
          @back="gotoPrev"
          @select-candidate="(candidateId) => { selectedCandidateId = candidateId }"
        />
        <PromptBuilderSaveStep
          v-else-if="activeStepKey === 'save'"
          :build-run-id="buildRunId"
          :course-name="courseName"
          :seed="seed"
          :selected-candidate-id="selectedCandidateId"
          :saving="saving"
          :save-error="saveError"
          :draft-name="saveDraftName"
          :draft-description="saveDraftDescription"
          :draft-name-touched="saveDraftNameTouched"
          :draft-description-touched="saveDraftDescriptionTouched"
          :save-mode="saveMode"
          @update:draft-name="saveDraftName = $event"
          @update:draft-description="saveDraftDescription = $event"
          @update:draft-name-touched="saveDraftNameTouched = $event"
          @update:draft-description-touched="saveDraftDescriptionTouched = $event"
          @update:save-mode="saveMode = $event"
          @mark-dirty="markDirty"
          @save="handleSave"
          @back="gotoPrev"
        />

        <PromptBuilderHistoryDraftDrawer
          v-model="historyDraftDrawerOpen"
          :drafts="historyDrafts"
          @select-draft="loadHistoryDraft"
        />
      </div>

      <footer v-if="activeStepKey === 'seed' || activeStepKey === 'prepare'" class="prompt-builder-page__actions">
        <div class="prompt-builder-page__status">
          <el-tag v-if="dirty" type="warning" size="small" effect="light">已修改未保存</el-tag>
          <el-tag v-else type="success" size="small" effect="light">已是最新</el-tag>
        </div>
        <div class="prompt-builder-page__buttons">
          <el-button type="primary" @click="gotoNext">下一步</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
