<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import PromptBuilderSeedStep from './prompt-builder/PromptBuilderSeedStep.vue'
import PromptBuilderPlaceholderStep from './prompt-builder/PromptBuilderPlaceholderStep.vue'
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

import { getBuildRun } from '../../api/knowledge-bases.js'

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
const courseName = ref(MOCK_COURSE_NAME)

const dirty = ref(false)
const saving = ref(false)
const saveError = ref('')

const activeStepKey = computed(() => resolveActiveStepKey(route.query))

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

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    ElMessage.info('历史草稿入库将在 Phase 1e 开放')
    return
  }
  if (seed.value && seed.value !== seedKey) {
    ElMessageBox.confirm('切换种子会重置后续步骤已有的进度，确定吗？', '切换种子', { type: 'warning' })
      .then(() => {
        seed.value = seedKey
        dirty.value = true
      })
      .catch(() => {})
    return
  }
  seed.value = seedKey
  dirty.value = true
}

async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    // Phase 1a 用 mock：500ms 延迟模拟保存请求
    await new Promise((resolve) => setTimeout(resolve, 500))
    // 注意：本期不真发请求，控制台打印 payload 供调试
    // eslint-disable-next-line no-console
    console.log('[Phase 1a mock] save payload', payload)
    dirty.value = false
    ElMessage.success('已保存到本次构建（mock）')
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
          :history-drafts="MOCK_HISTORY_DRAFTS"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'prepare'"
          step-key="prepare"
          title="构建准备材料"
          description="生成调优样本与校准集，并完成人工标注。"
          phase="Phase 1b"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'candidates'"
          step-key="candidates"
          title="生成候选提示词"
          description="基于校准集生成多版候选提示词，挑选要参与评分的候选。"
          phase="Phase 1c"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'scoring'"
          step-key="scoring"
          title="抽取评分"
          description="在校准集上跑候选提示词，按综合分排序选出最佳候选。"
          phase="Phase 1d"
        />
        <PromptBuilderSaveStep
          v-else-if="activeStepKey === 'save'"
          :build-run-id="buildRunId"
          :course-name="courseName"
          :seed="seed"
          :saving="saving"
          :save-error="saveError"
          @save="handleSave"
          @back="gotoPrev"
        />
      </div>

      <footer v-if="activeStepKey !== 'save'" class="prompt-builder-page__actions">
        <div class="prompt-builder-page__status">
          <span v-if="dirty" class="dirty">● 已修改未保存</span>
          <span v-else>已是最新</span>
        </div>
        <div class="prompt-builder-page__buttons">
          <el-button v-if="activeStepKey !== 'seed'" @click="gotoPrev">上一步</el-button>
          <el-button type="primary" @click="gotoNext">下一步</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
