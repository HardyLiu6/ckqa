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
  MOCK_HISTORY_DRAFTS,
  MOCK_COURSE_NAME,
} from './prompt-builder/mocks/index.js'

import { getBuildRun, saveBuildRunCustomPromptDraft } from '../../api/knowledge-bases.js'
import { getSeedAvailability } from '../../api/prompt-tune-pipeline.js'

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
    // Phase 1e：从历史草稿列表中选取最新一条作为 mock 演示
    const latest = MOCK_HISTORY_DRAFTS[0]
    if (latest) {
      seed.value = 'history_draft'
      selectedCandidateId.value = latest.sourceCandidateId ?? ''
      saveDraftName.value = latest.name
      saveDraftDescription.value = latest.description ?? ''
      saveDraftNameTouched.value = true
      dirty.value = true
      ElMessage.success(`已加载历史草稿「${latest.name}」`)
    } else {
      ElMessage.info('暂无可用的历史草稿')
    }
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

async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    // Phase 1a 用 mock：500ms 延迟模拟保存请求
    await new Promise((resolve) => setTimeout(resolve, 500))
    // 注意：本期不真发请求，控制台打印 payload 供调试
    // eslint-disable-next-line no-console
    console.log('[Phase 1e mock] save payload', payload)
    dirty.value = false
    if (payload.saveMode === 'build_run_with_history') {
      ElMessage.success('已保存到本次构建并入库历史草稿（mock）')
    } else {
      ElMessage.success('已保存到本次构建（mock）')
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
          :history-drafts="MOCK_HISTORY_DRAFTS"
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
