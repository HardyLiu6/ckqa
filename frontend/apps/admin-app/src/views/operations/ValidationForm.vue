<script setup>
/**
 * 知识库验证页 · 左侧表单子组件（M7 · 任务 5.3 拆分产物）。
 *
 * 父组件 `KbValidationPage.vue` 因模板 + 样式超过 400 行预算，按 design §5.5 的
 * 指引抽出 `ValidationForm.vue / ValidationResult.vue` 两个子组件。
 *
 * 职责：
 * - 渲染 KB 选择（`el-select`）、问题输入（`el-input type="textarea"`）、
 *   验证模式（`el-radio-group`，映射 `MODE_LABELS`）与「发起验证」按钮；
 * - 通过 `v-model:selectedKbId / v-model:question / v-model:mode` 与父组件双向同步；
 * - 「发起验证」按钮受 `authStore.canAccess(['qa:write'])` 守护，disable 条件在父组件计算；
 * - 所有文案走 `FORM_COPY / MODE_LABELS`，不在模板中新增裸字符串。
 */
import { computed } from 'vue'

import { useAuthStore } from '../../stores/auth.js'
import { FORM_COPY, MODE_LABELS } from './kb-validation-copy.js'

const props = defineProps({
  knowledgeBases: { type: Array, default: () => [] },
  selectedKbId: { type: [String, Number, null], default: null },
  question: { type: String, default: '' },
  mode: { type: String, default: 'basic' },
  canSubmit: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:selectedKbId',
  'update:question',
  'update:mode',
  'submit',
])

const authStore = useAuthStore()

const kbModel = computed({
  get: () => props.selectedKbId,
  set: (value) => emit('update:selectedKbId', value),
})

const questionModel = computed({
  get: () => props.question,
  set: (value) => emit('update:question', value ?? ''),
})

const modeModel = computed({
  get: () => props.mode,
  set: (value) => emit('update:mode', value ?? 'basic'),
})

function handleSubmit() { emit('submit') }
</script>

<template>
  <article
    class="kb-validation-form"
    data-testid="kb-validation-form"
  >
    <h3 class="kb-validation-form__section-title">{{ FORM_COPY.title }}</h3>
    <el-form label-position="top" @submit.prevent>
      <el-form-item :label="FORM_COPY.kbLabel">
        <el-select
          v-model="kbModel"
          filterable
          class="kb-validation-form__kb-select"
          :placeholder="FORM_COPY.kbLabel"
          data-testid="kb-validation-kb-select"
        >
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :value="kb.id"
            :label="kb.name"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="FORM_COPY.questionLabel">
        <el-input
          v-model="questionModel"
          type="textarea"
          :rows="3"
          :maxlength="500"
          show-word-limit
          :placeholder="FORM_COPY.questionLabel"
          data-testid="kb-validation-question"
        />
      </el-form-item>

      <el-form-item :label="FORM_COPY.modeLabel">
        <el-radio-group v-model="modeModel" data-testid="kb-validation-mode">
          <el-radio-button
            v-for="(label, key) in MODE_LABELS"
            :key="key"
            :value="key"
            :label="key"
          >
            {{ label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-button
        v-if="authStore.canAccess(['qa:write'])"
        type="primary"
        :disabled="!canSubmit"
        data-testid="kb-validation-start"
        @click="handleSubmit"
      >
        {{ FORM_COPY.submit }}
      </el-button>
    </el-form>
  </article>
</template>

<style scoped lang="scss">
.kb-validation-form {
  padding: var(--ckqa-space-5);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.kb-validation-form__section-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.kb-validation-form__kb-select {
  width: 100%;
}
</style>
