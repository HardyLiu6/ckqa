<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import PromptBuilderSeedStep from './prompt-builder/PromptBuilderSeedStep.vue'
import PromptBuilderEditStep from './prompt-builder/PromptBuilderEditStep.vue'
import PromptBuilderPreviewStep from './prompt-builder/PromptBuilderPreviewStep.vue'
import { utf8ByteLength } from './prompt-builder/byte-counter.js'

import { getBuildRun, saveBuildRunCustomPromptDraft } from '../../api/knowledge-bases.js'

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
const drafts = ref({ extract_graph: '' })
const templates = ref({ extract_graph: '' })  // 已知限制：本期种子内容未从后端拉取，"还原至模板"等价于"清空内容"。后续版本接入种子内容后再做真正还原。
const activeStep = ref('seed')
const dirty = ref(false)
const saving = ref(false)
const saveError = ref(null)

const BUILDER_STEPS = [
  { key: 'seed',    label: '选模板',    detail: '从模板或现有版本起步' },
  { key: 'edit',    label: '分块编辑',  detail: '编辑提示词内容' },
  { key: 'preview', label: '预览 + 保存', detail: '确认后回到构建向导' },
]

const stepStatuses = computed(() => {
  const order = ['seed', 'edit', 'preview']
  const currentIdx = order.indexOf(activeStep.value)
  return BUILDER_STEPS.map((step, idx) => ({
    ...step,
    status: idx < currentIdx ? 'done' : idx === currentIdx ? 'ready' : 'blocked',
  }))
})

const canGoNext = computed(() => {
  if (activeStep.value === 'seed') return Boolean(seed.value) && seed.value !== 'history_draft'
  if (activeStep.value === 'edit') return drafts.value.extract_graph?.trim().length > 0
        && utf8ByteLength(drafts.value.extract_graph) <= 32 * 1024
  return true
})

const canSave = computed(() => dirty.value && !saving.value && canGoNext.value)
const canReturn = computed(() => !saving.value && canGoNext.value)  // 已有草稿未修改时也允许返回

const promptTitle = '手动调优提示词'

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
    if (draft) {
      seed.value = draft.seed ?? null
      drafts.value.extract_graph = draft.prompts?.extract_graph?.content ?? ''
      activeStep.value = 'edit'
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

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') return
  if (drafts.value.extract_graph && seed.value !== seedKey) {
    ElMessageBox.confirm('切换种子会清空当前编辑，确定吗？', '切换种子', { type: 'warning' })
      .then(() => {
        seed.value = seedKey
        drafts.value.extract_graph = ''
        dirty.value = true
      })
      .catch(() => {})
    return
  }
  seed.value = seedKey
  dirty.value = true
}

function handleEditContent(value) {
  drafts.value.extract_graph = value
  dirty.value = true
}

function gotoStep(stepKey) {
  if (!BUILDER_STEPS.some((s) => s.key === stepKey)) return
  activeStep.value = stepKey
}

function gotoNext() {
  const order = ['seed', 'edit', 'preview']
  const idx = order.indexOf(activeStep.value)
  if (idx >= 0 && idx < order.length - 1 && canGoNext.value) {
    activeStep.value = order[idx + 1]
  }
}

function gotoPrev() {
  const order = ['seed', 'edit', 'preview']
  const idx = order.indexOf(activeStep.value)
  if (idx > 0) activeStep.value = order[idx - 1]
}

async function saveDraft({ navigateBack }) {
  if (!canSave.value) return
  saving.value = true
  saveError.value = null
  try {
    await saveBuildRunCustomPromptDraft(buildRunId.value, {
      seed: seed.value,
      prompts: { extract_graph: { content: drafts.value.extract_graph } },
    })
    if (navigateBack) {
      dirty.value = false  // 导航前清 dirty，避免 onBeforeRouteLeave 拦截
      try {
        await router.push({
          name: 'knowledge-base-build',
          params: { kbId: kbId.value },
          query: {
            buildRunId: buildRunId.value,
            step: 'prompt',
            promptStrategy: 'custom_pipeline',
          },
        })
      } catch (navErr) {
        dirty.value = true  // 导航失败时恢复 dirty 状态
        throw navErr
      }
    } else {
      dirty.value = false  // 仅暂存时，保存成功后清 dirty
    }
  } catch (e) {
    saveError.value = e?.message ?? '保存失败，请重试'
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

function returnToWizardWithStrategy() {
  // 已有草稿未修改时，直接返回向导并携带 promptStrategy，确保策略不丢失
  router.push({
    name: 'knowledge-base-build',
    params: { kbId: kbId.value },
    query: {
      buildRunId: buildRunId.value,
      step: 'prompt',
      promptStrategy: 'custom_pipeline',
    },
  })
}
</script>

<template>
  <section class="prompt-builder-page">
    <header class="prompt-builder-page__header">
      <div>
        <h2>{{ promptTitle }}</h2>
        <p v-if="buildRunId">为本次构建（Build Run ID：{{ buildRunId }}）设计提示词。</p>
      </div>
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        type="default"
        @click="returnToWizard"
      >
        <ChevronLeft class="button-icon" :size="16" aria-hidden="true" />
        返回构建向导
      </el-button>
    </header>

    <RetryPanel
      v-if="error"
      :error="error"
      @retry="returnToWizard"
    />

    <template v-else-if="!loading">
      <WorkflowStepper
        :active-key="activeStep"
        :steps="stepStatuses"
        @update:active-key="gotoStep"
      />

      <div class="prompt-builder-page__body">
        <PromptBuilderSeedStep
          v-if="activeStep === 'seed'"
          :seed="seed"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderEditStep
          v-else-if="activeStep === 'edit'"
          :extract-graph-content="drafts.extract_graph"
          :template-content="templates.extract_graph"
          @update:extract-graph-content="handleEditContent"
        />
        <PromptBuilderPreviewStep
          v-else-if="activeStep === 'preview'"
          :extract-graph-content="drafts.extract_graph"
          :build-run-id="buildRunId"
        />
      </div>

      <footer class="prompt-builder-page__actions">
        <div class="prompt-builder-page__status">
          <span v-if="dirty" class="dirty">● 已修改未保存</span>
          <span v-else-if="saving">保存中…</span>
          <span v-else-if="saveError" class="error">{{ saveError }}</span>
          <span v-else>已是最新</span>
        </div>
        <div class="prompt-builder-page__buttons">
          <el-button v-if="activeStep !== 'seed'" class="ckqa-el-button" @click="gotoPrev">上一步</el-button>
          <el-button
            v-if="activeStep === 'edit'"
            class="ckqa-el-button ckqa-el-button--ghost"
            :disabled="!canSave"
            @click="saveDraft({ navigateBack: false })"
          >
            暂存草稿
          </el-button>
          <el-button
            v-if="activeStep !== 'preview'"
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            :disabled="!canGoNext"
            @click="gotoNext"
          >
            下一步
          </el-button>
          <el-button
            v-if="activeStep === 'preview'"
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            :disabled="!canReturn"
            @click="dirty ? saveDraft({ navigateBack: true }) : returnToWizardWithStrategy()"
          >
            {{ dirty ? '保存并返回' : '返回向导' }}
          </el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
