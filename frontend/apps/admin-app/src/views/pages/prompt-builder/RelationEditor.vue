<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import { filterRelationTypesByEndpoints, tryReverseRelation } from './relation-types-model.js'

const props = defineProps({
  /** 当前样本的实体列表，用于源/目标下拉 */
  entities: { type: Array, default: () => [] },
})

const emit = defineEmits(['submit', 'cancel'])

const sourceEntityId = ref('')
const targetEntityId = ref('')
const type = ref('')
const evidence = ref('')
const submitAttempted = ref(false)

const sourceEntity = computed(() => props.entities.find((e) => e.id === sourceEntityId.value))
const targetEntity = computed(() => props.entities.find((e) => e.id === targetEntityId.value))

const isSelfLoop = computed(() =>
  Boolean(sourceEntityId.value && sourceEntityId.value === targetEntityId.value)
)

const availableRelationTypes = computed(() => {
  if (!sourceEntity.value || !targetEntity.value) return []
  // 排序：把 related_to 永远放最后；其余按 RELATION_TYPES 原顺序
  // 用 spread 先复制再 sort，避免原地修改 filterRelationTypesByEndpoints 内部缓存
  const all = filterRelationTypesByEndpoints({
    sourceType: sourceEntity.value.type,
    targetType: targetEntity.value.type,
  })
  return [...all].sort((a, b) => {
    if (a.name === 'related_to') return 1
    if (b.name === 'related_to') return -1
    return 0
  })
})

// 当源/目标变化时，如果当前选择的 type 不再合法，清空它
watch(availableRelationTypes, (types) => {
  if (type.value && !types.some((t) => t.name === type.value)) {
    type.value = ''
  }
})

const canSubmit = computed(() =>
  Boolean(sourceEntityId.value && targetEntityId.value && type.value) && !isSelfLoop.value
)

function entityLabel(entity) {
  return `${entity.name}（${entity.type}）`
}

function relationOptionLabel(r) {
  return `${r.name}（${r.label_zh}）`
}

function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  emit('submit', {
    sourceEntityId: sourceEntityId.value,
    targetEntityId: targetEntityId.value,
    type: type.value,
    evidence: evidence.value.trim() || undefined,
  })
  reset()
}

function handleCancel() {
  reset()
  emit('cancel')
}

function reset() {
  sourceEntityId.value = ''
  targetEntityId.value = ''
  type.value = ''
  evidence.value = ''
  submitAttempted.value = false
}

const reverseHint = computed(() => {
  if (!sourceEntity.value || !targetEntity.value) return null
  if (isSelfLoop.value) return null
  // 当正向只有 related_to 兜底（没有更具体关系），且反向有更具体关系时才提示
  return tryReverseRelation({
    sourceType: sourceEntity.value.type,
    targetType: targetEntity.value.type,
  })
})

function swapDirection() {
  const tmp = sourceEntityId.value
  sourceEntityId.value = targetEntityId.value
  targetEntityId.value = tmp
  // type 会被 watch(availableRelationTypes) 自动清空
}
</script>

<template>
  <form class="relation-editor" @submit.prevent="handleSubmit">
    <div class="relation-editor__row">
      <label>
        <span class="relation-editor__label">源实体 *</span>
        <el-select v-model="sourceEntityId" size="default" placeholder="选择源实体" style="width: 100%">
          <el-option
            v-for="e in entities"
            :key="e.id"
            :value="e.id"
            :label="entityLabel(e)"
          />
        </el-select>
      </label>
      <label>
        <span class="relation-editor__label">目标实体 *</span>
        <el-select v-model="targetEntityId" size="default" placeholder="选择目标实体" style="width: 100%">
          <el-option
            v-for="e in entities"
            :key="e.id"
            :value="e.id"
            :label="entityLabel(e)"
          />
        </el-select>
      </label>
    </div>
    <p v-if="isSelfLoop" class="relation-editor__hint relation-editor__hint--error">
      ⚠ 源实体与目标实体不能相同（自环关系本系统暂不支持）
    </p>
    <label class="relation-editor__row relation-editor__row--full">
      <span class="relation-editor__label">关系类型 *</span>
      <el-select
        v-model="type"
        size="default"
        :placeholder="sourceEntity && targetEntity ? '选择关系类型' : '请先选择两端实体'"
        :disabled="!sourceEntity || !targetEntity || isSelfLoop"
        style="width: 100%"
      >
        <el-option
          v-for="r in availableRelationTypes"
          :key="r.name"
          :value="r.name"
          :label="relationOptionLabel(r)"
        />
      </el-select>
      <p v-if="type === 'related_to'" class="relation-editor__hint">
        💡 related_to 是保底关系，仅在没有更具体关系时使用。
      </p>
      <p v-if="sourceEntity && targetEntity && !isSelfLoop && availableRelationTypes.length === 0" class="relation-editor__hint relation-editor__hint--error">
        ⚠ 两端类型「{{ sourceEntity.type }} → {{ targetEntity.type }}」之间没有 schema 合法关系，请检查实体类型是否正确。
      </p>
      <p v-if="reverseHint?.hasReverse" class="relation-editor__hint relation-editor__hint--reverse">
        💡 当前方向「{{ sourceEntity.type }} → {{ targetEntity.type }}」仅有 related_to 兜底关系；
        反向「{{ targetEntity.type }} → {{ sourceEntity.type }}」可使用更具体关系
        ({{ reverseHint.reverseTypes.map((r) => r.name).join('/') }})。
        <button type="button" class="relation-editor__swap-btn" @click="swapDirection">调换方向</button>
      </p>
    </label>
    <label class="relation-editor__row relation-editor__row--full">
      <span class="relation-editor__label">证据（可选）</span>
      <el-input
        v-model="evidence"
        placeholder="例如：原文中支持这个关系的句子"
      />
    </label>
    <div v-if="submitAttempted && !canSubmit && !isSelfLoop" class="relation-editor__error">
      请完整选择源实体、目标实体和关系类型
    </div>
    <div class="relation-editor__actions">
      <el-button size="small" @click="handleCancel">取消</el-button>
      <el-button type="primary" size="small" native-type="submit" :disabled="!canSubmit">
        添加
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.relation-editor {
  border: 1px dashed #c4b5fd;
  border-radius: 8px;
  padding: 16px;
  background: #faf5ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.relation-editor__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.relation-editor__row--full {
  grid-template-columns: 1fr;
  display: block;
}
.relation-editor__label {
  display: block;
  font-size: 12px;
  color: #57534e;
  margin-bottom: 4px;
}
.relation-editor__hint {
  font-size: 12px;
  color: #57534e;
  margin-top: 4px;
}
.relation-editor__hint--error {
  color: #b45309;
}
.relation-editor__hint--reverse {
  color: #6366f1;
}
.relation-editor__swap-btn {
  margin-left: 8px;
  background: #6366f1;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
}
.relation-editor__swap-btn:hover {
  background: #4f46e5;
}
.relation-editor__error {
  font-size: 12px;
  color: #dc2626;
}
.relation-editor__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
