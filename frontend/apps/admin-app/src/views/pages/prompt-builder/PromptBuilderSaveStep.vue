<script setup>
import { computed, ref } from 'vue'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from './save-step-model.js'

const props = defineProps({
  buildRunId: { type: [String, Number], default: '' },
  courseName: { type: String, default: '' },
  seed: { type: String, default: null },
  saving: { type: Boolean, default: false },
  saveError: { type: String, default: '' },
})

const emit = defineEmits(['save', 'back'])

const draftName = ref(buildDefaultDraftName({ courseName: props.courseName, seed: props.seed }))
const draftDescription = ref('')

const validation = computed(() =>
  validateSaveForm({ name: draftName.value, seed: props.seed })
)

const canSave = computed(() => validation.value.valid && !props.saving)

const seedLabel = computed(() => {
  if (props.seed === 'system_default') return '系统默认'
  if (props.seed === 'graphrag_tuned') return 'GraphRAG 自动调优'
  return '—'
})

function handleSubmit() {
  if (!canSave.value) return
  const payload = buildSaveDraftPayload({
    seed: props.seed,
    name: draftName.value,
    description: draftDescription.value,
  })
  emit('save', payload)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-save-step">
    <header class="prompt-builder-step__header">
      <h3>预览保存</h3>
      <p>
        Phase 1a 简版：仅命名 + 保存到本次构建。
        完整 prompt 预览、保存范围选择、入库历史草稿将在 Phase 1e 接入。
      </p>
    </header>

    <div class="prompt-builder-save-step__body">
      <div class="form-row">
        <label class="form-row__label">草稿名</label>
        <el-input v-model="draftName" placeholder="如：操作系统 · 系统默认 · 2026-05-14" />
        <p v-if="validation.errors.name" class="form-row__error">{{ validation.errors.name }}</p>
      </div>

      <div class="form-row">
        <label class="form-row__label">说明 <span class="form-row__optional">（选填）</span></label>
        <el-input v-model="draftDescription" type="textarea" :rows="3" placeholder="例如：初版草稿，沿用 GraphRAG 默认。" />
      </div>

      <div class="form-row">
        <label class="form-row__label">来源记录</label>
        <div class="prompt-builder-save-step__source">
          <div><span>课程</span><strong>{{ courseName || '—' }}</strong></div>
          <div><span>构建运行</span><strong>{{ buildRunId || '—' }}</strong></div>
          <div><span>选定种子</span><strong>{{ seedLabel }}</strong></div>
        </div>
        <p v-if="validation.errors.seed" class="form-row__error">{{ validation.errors.seed }}</p>
      </div>

      <p v-if="saveError" class="form-row__error">{{ saveError }}</p>

      <div class="prompt-builder-save-step__actions">
        <el-button @click="$emit('back')" :disabled="saving">← 返回上一步</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSubmit">
          ✓ 保存并返回构建向导
        </el-button>
      </div>
    </div>
  </section>
</template>
