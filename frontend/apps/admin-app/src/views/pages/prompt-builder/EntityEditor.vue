<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'
import { findDuplicateEntity } from './entity-id-generator.js'

const props = defineProps({
  /** 当前样本中已有的实体列表，用于重名检测 */
  existingEntities: { type: Array, default: () => [] },
  /**
   * 预填实体对象，新建/编辑两个场景共用：
   * - 拖选场景：{ name, spanStart, spanEnd }（缺 type/description 时各 fallback 到默认值）
   * - 编辑 AI 候选场景：{ name, type, description, spanStart, spanEnd, ... }
   * - null：纯新建，全部清空
   *
   * 兼容老 props（prefilledName / prefilledSpan）作为更细粒度的预填入口，
   * 但当 prefilledEntity 非空时优先使用它。
   */
  prefilledEntity: { type: Object, default: null },
  prefilledName: { type: String, default: '' },
  prefilledSpan: {
    type: Object,
    default: null,
    validator: (val) => val === null || (
      Number.isInteger(val.spanStart) && Number.isInteger(val.spanEnd)
    ),
  },
  /** 编辑模式标志：用于按钮文案"添加" → "保存修改" */
  editMode: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'cancel'])

const name = ref('')
const type = ref('Concept')
const description = ref('')
const spanStart = ref(null)
const spanEnd = ref(null)
const submitAttempted = ref(false)

// 初始化预填字段：prefilledEntity 优先，没有则 fallback 到细粒度 props
function applyPrefill() {
  const e = props.prefilledEntity
  if (e) {
    name.value = e.name ?? ''
    type.value = e.type || 'Concept'
    description.value = e.description ?? ''
    spanStart.value = Number.isInteger(e.spanStart) ? e.spanStart : null
    spanEnd.value = Number.isInteger(e.spanEnd) ? e.spanEnd : null
  } else {
    name.value = props.prefilledName ?? ''
    type.value = 'Concept'
    description.value = ''
    if (props.prefilledSpan) {
      spanStart.value = props.prefilledSpan.spanStart
      spanEnd.value = props.prefilledSpan.spanEnd
    } else {
      spanStart.value = null
      spanEnd.value = null
    }
  }
  submitAttempted.value = false
}
applyPrefill()

// prefill 变化时重置表单（场景：dialog 关闭重开传入新候选）
watch(() => [props.prefilledEntity, props.prefilledName, props.prefilledSpan], applyPrefill, { deep: true })

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
  if (Number.isInteger(spanStart.value) && Number.isInteger(spanEnd.value)) {
    payload.spanStart = spanStart.value
    payload.spanEnd = spanEnd.value
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
  spanStart.value = null
  spanEnd.value = null
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
        {{ editMode ? '保存修改' : '添加' }}
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
