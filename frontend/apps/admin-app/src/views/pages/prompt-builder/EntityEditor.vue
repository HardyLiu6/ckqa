<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'
import { findDuplicateEntity } from './entity-id-generator.js'

const props = defineProps({
  /** 当前样本中已有的实体列表，用于重名检测 */
  existingEntities: { type: Array, default: () => [] },
  /** 拖选预填的实体名（来自原文选区） */
  prefilledName: { type: String, default: '' },
  /** 拖选预填的字符位置 [spanStart, spanEnd]，提交时透传到 payload */
  prefilledSpan: {
    type: Object,
    default: null,
    validator: (val) => val === null || (
      Number.isInteger(val.spanStart) && Number.isInteger(val.spanEnd)
    ),
  },
})

const emit = defineEmits(['submit', 'cancel'])

const name = ref(props.prefilledName ?? '')
const type = ref('Concept')
const description = ref('')
const submitAttempted = ref(false)

watch(() => props.prefilledName, (val) => {
  if (val) name.value = val
})

const trimmedName = computed(() => name.value.trim())

const duplicate = computed(() =>
  trimmedName.value ? findDuplicateEntity(props.existingEntities, trimmedName.value, type.value) : null
)

const canSubmit = computed(() => trimmedName.value.length > 0)

function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  const payload = {
    name: trimmedName.value,
    type: type.value,
    description: description.value.trim() || undefined,
  }
  if (props.prefilledSpan) {
    payload.spanStart = props.prefilledSpan.spanStart
    payload.spanEnd = props.prefilledSpan.spanEnd
  }
  emit('submit', payload)
  reset()
}

function handleCancel() {
  reset()
  emit('cancel')
}

function reset() {
  name.value = ''
  type.value = 'Concept'
  description.value = ''
  submitAttempted.value = false
}
</script>

<template>
  <form class="entity-editor" @submit.prevent="handleSubmit">
    <div class="entity-editor__row">
      <label>
        <span class="entity-editor__label">实体名 *</span>
        <el-input
          v-model="name"
          placeholder="例如：进程"
          size="default"
          :class="{ 'entity-editor__input--error': submitAttempted && !canSubmit }"
        />
      </label>
      <label>
        <span class="entity-editor__label">类型 *</span>
        <el-select v-model="type" size="default" style="width: 100%">
          <el-option
            v-for="t in ENTITY_TYPES"
            :key="t.name"
            :value="t.name"
            :label="`${t.name}（${t.label_zh}）`"
          />
        </el-select>
      </label>
    </div>
    <label class="entity-editor__row entity-editor__row--full">
      <span class="entity-editor__label">说明（可选）</span>
      <el-input
        v-model="description"
        type="textarea"
        :rows="2"
        placeholder="一句话说明这个实体的含义或边界"
      />
    </label>
    <div v-if="duplicate" class="entity-editor__warn">
      ⚠ 已存在同名同类型实体「{{ duplicate.name }}」（id: {{ duplicate.id }}），确认继续添加？
    </div>
    <div v-if="submitAttempted && !canSubmit" class="entity-editor__error">
      实体名不能为空
    </div>
    <div class="entity-editor__actions">
      <el-button size="small" @click="handleCancel">取消</el-button>
      <el-button type="primary" size="small" native-type="submit" :disabled="!canSubmit">
        添加
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.entity-editor {
  border: 1px dashed #c4b5fd;
  border-radius: 8px;
  padding: 16px;
  background: #faf5ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.entity-editor__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.entity-editor__row--full {
  grid-template-columns: 1fr;
  display: block;
}
.entity-editor__label {
  display: block;
  font-size: 12px;
  color: #57534e;
  margin-bottom: 4px;
}
.entity-editor__warn {
  font-size: 12px;
  color: #b45309;
  background: #fef3c7;
  padding: 6px 10px;
  border-radius: 4px;
}
.entity-editor__error {
  font-size: 12px;
  color: #dc2626;
}
.entity-editor__input--error :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dc2626 inset;
}
.entity-editor__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
