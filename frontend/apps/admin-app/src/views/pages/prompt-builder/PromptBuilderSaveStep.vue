<script setup>
import { computed, watch } from 'vue'
import PromptDisplay from './PromptDisplay.vue'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from './save-step-model.js'
import { resolveCandidatePromptText, MOCK_CANDIDATES, MOCK_SCORING_REPORT } from './mocks/index.js'

const props = defineProps({
  buildRunId: { type: [String, Number], default: '' },
  courseName: { type: String, default: '' },
  seed: { type: String, default: null },
  selectedCandidateId: { type: String, default: '' },
  saving: { type: Boolean, default: false },
  saveError: { type: String, default: '' },
  draftName: { type: String, default: '' },
  draftDescription: { type: String, default: '' },
  draftNameTouched: { type: Boolean, default: false },
  draftDescriptionTouched: { type: Boolean, default: false },
  saveMode: { type: String, default: 'build_run_with_history' },
})

const emit = defineEmits([
  'save',
  'back',
  'mark-dirty',
  'update:draftName',
  'update:draftDescription',
  'update:draftNameTouched',
  'update:draftDescriptionTouched',
  'update:saveMode',
])

// 候选信息
const candidateInfo = computed(() =>
  MOCK_CANDIDATES.find((c) => c.candidateId === props.selectedCandidateId) ?? null
)

const candidateScore = computed(() => {
  if (!props.selectedCandidateId || !MOCK_SCORING_REPORT) return null
  const detail = MOCK_SCORING_REPORT.candidateDetails?.find(
    (d) => d.candidateId === props.selectedCandidateId
  )
  return detail?.compositeScore ?? null
})

// prompt 文本
const promptText = computed(() => resolveCandidatePromptText(props.selectedCandidateId))

// 自动生成默认名（仅在用户未手动修改时）
watch(
  () => [props.courseName, props.seed, props.selectedCandidateId],
  () => {
    if (!props.draftNameTouched) {
      const defaultName = buildDefaultDraftName({ courseName: props.courseName, seed: props.seed })
      emit('update:draftName', defaultName)
    }
  },
  { immediate: true }
)

const validation = computed(() =>
  validateSaveForm({ name: props.draftName, seed: props.seed })
)

const canSave = computed(() => validation.value.valid && !props.saving)

const seedLabel = computed(() => {
  if (props.seed === 'system_default') return '系统默认'
  if (props.seed === 'graphrag_tuned') return 'GraphRAG 自动调优'
  return '—'
})

function handleNameInput(val) {
  emit('update:draftName', val)
  if (!props.draftNameTouched) emit('update:draftNameTouched', true)
  emit('mark-dirty')
}

function handleDescInput(val) {
  emit('update:draftDescription', val)
  if (!props.draftDescriptionTouched) emit('update:draftDescriptionTouched', true)
  emit('mark-dirty')
}

function handleSaveModeChange(val) {
  emit('update:saveMode', val)
  emit('mark-dirty')
}

function handleSubmit() {
  if (!canSave.value) return
  const payload = buildSaveDraftPayload({
    seed: props.seed,
    name: props.draftName,
    description: props.draftDescription,
    selectedCandidate: props.selectedCandidateId,
    candidateDisplayName: candidateInfo.value?.displayNameZh ?? '',
    compositeScore: candidateScore.value,
    saveMode: props.saveMode,
  })
  emit('save', payload)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-save-step--full">
    <header class="prompt-builder-step__header">
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>预览保存</h3>
        <p>确认选中的候选提示词，命名并保存。</p>
      </div>
    </header>

    <div class="save-step-layout">
      <!-- 左侧：prompt 预览 -->
      <div class="save-step-preview">
        <div class="save-step-preview__meta">
          <span v-if="candidateInfo" class="ann-pill ann-pill--gold">{{ candidateInfo.displayNameZh }}</span>
          <span v-if="candidateScore != null" class="ann-pill">综合 {{ (candidateScore * 100).toFixed(0) }}%</span>
        </div>
        <h4 class="save-step-preview__title">选中候选提示词全文</h4>
        <p class="save-step-preview__sub">以下为即将保存的完整 prompt 文本，可切换视图模式查看。</p>
        <PromptDisplay v-if="promptText" :text="promptText" default-mode="rich" />
        <div v-else class="ann-text-muted">未找到该候选的提示词文本</div>
      </div>

      <!-- 右侧：保存表单 -->
      <div class="save-step-form">
        <div class="save-step-form__icon">💾</div>

        <div class="form-row">
          <label class="form-row__label">草稿名</label>
          <el-input :model-value="draftName" placeholder="如：操作系统 · 系统默认 · 2026-05-14" @input="handleNameInput" />
          <p v-if="draftNameTouched && validation.errors.name" class="form-row__error">{{ validation.errors.name }}</p>
        </div>

        <div class="form-row">
          <label class="form-row__label">说明 <span class="form-row__optional">（选填）</span></label>
          <el-input :model-value="draftDescription" type="textarea" :rows="3" placeholder="例如：初版草稿，沿用 GraphRAG 默认。" @input="handleDescInput" />
        </div>

        <div class="form-row">
          <label class="form-row__label">来源记录</label>
          <div class="save-step-form__source">
            <div><span>课程</span><strong>{{ courseName || '—' }}</strong></div>
            <div><span>构建运行</span><strong>{{ buildRunId || '—' }}</strong></div>
            <div><span>选定种子</span><strong>{{ seedLabel }}</strong></div>
            <div><span>选中候选</span><strong>{{ (candidateInfo?.displayNameZh ?? selectedCandidateId) || '—' }}</strong></div>
          </div>
          <p v-if="validation.errors.seed" class="form-row__error">{{ validation.errors.seed }}</p>
        </div>

        <div class="form-row">
          <label class="form-row__label">保存范围</label>
          <el-radio-group :model-value="saveMode" @update:model-value="handleSaveModeChange">
            <el-radio value="build_run_with_history">保存到本次构建 + 入库历史草稿</el-radio>
            <el-radio value="build_run_only">仅保存到本次构建</el-radio>
          </el-radio-group>
        </div>

        <p v-if="saveError" class="form-row__error">{{ saveError }}</p>

        <div class="save-step-form__actions">
          <el-button @click="$emit('back')" :disabled="saving">← 返回上一步</el-button>
          <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSubmit">
            ✓ 保存并返回构建向导
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>
