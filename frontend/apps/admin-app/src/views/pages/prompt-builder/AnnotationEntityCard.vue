<script setup>
import { computed } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'

const props = defineProps({
  /** 实体对象，含 id / name / type / description / source / confidence / originalType / typeOutOfSchema */
  entity: { type: Object, required: true },
})

defineEmits(['accept', 'reject', 'delete', 'edit'])

const isReused    = computed(() => props.entity.source === 'reused')
const isSuggested = computed(() => props.entity.source === 'ai_suggested')

const typeLabel = computed(() => {
  const found = ENTITY_TYPES.find((e) => e.name === props.entity.type)
  return found ? found.label_zh : props.entity.type
})

// AI 候选场景：当后端发现 LLM 输出的 type 不在 schema 11 种内时，
// 会兜底为 Concept 并塞入 typeOutOfSchema:true + originalType:'<原始类型>'
// 用户可以直接采纳（接受 Concept 兜底）或进 EntityEditor 改类型再采纳
const isTypeOutOfSchema = computed(() => isSuggested.value && props.entity.typeOutOfSchema === true)
const typeWarningTitle = computed(() =>
  isTypeOutOfSchema.value
    ? `AI 原始类型 "${props.entity.originalType}" 不在 schema 11 种内，已兜底为 Concept`
    : ''
)
</script>

<template>
  <div
    class="entity-chip"
    :class="{
      'entity-chip--reused': isReused,
      'entity-chip--suggested': isSuggested,
    }"
  >
    <div class="entity-chip__top">
      <span v-if="isReused" class="entity-chip__badge" aria-hidden="true">♻</span>
      <span v-else-if="isSuggested" class="entity-chip__badge entity-chip__badge--ai" aria-hidden="true">✨</span>
      <span class="entity-chip__name">{{ entity.name }}</span>
      <span class="entity-chip__type">{{ typeLabel }}</span>
      <span
        v-if="isTypeOutOfSchema"
        class="entity-chip__type-warning"
        :title="typeWarningTitle"
      >⚠ 类型已兜底</span>
    </div>
    <div v-if="entity.description || (isSuggested && entity.confidence != null)" class="entity-chip__meta">
      <span v-if="entity.description">{{ entity.description }}</span>
      <span v-if="isSuggested && entity.confidence != null" class="entity-chip__conf">
        置信度 {{ (entity.confidence * 100).toFixed(0) }}%
      </span>
    </div>
    <div class="entity-chip__actions">
      <template v-if="isSuggested">
        <button class="entity-chip__btn entity-chip__btn--edit" title="编辑后采纳" @click="$emit('edit', entity.id)">✎</button>
        <button class="entity-chip__btn entity-chip__btn--accept" @click="$emit('accept', entity.id)">✓</button>
        <button class="entity-chip__btn entity-chip__btn--reject" @click="$emit('reject', entity.id)">✕</button>
      </template>
      <template v-else>
        <button class="entity-chip__btn entity-chip__btn--delete" @click="$emit('delete', entity.id)">×</button>
      </template>
    </div>
  </div>
</template>
