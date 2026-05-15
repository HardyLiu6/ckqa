<script setup>
import { computed } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'

const props = defineProps({
  /** 实体对象，含 id / name / type / description / source / confidence */
  entity: { type: Object, required: true },
})

defineEmits(['accept', 'reject', 'delete'])

const isReused    = computed(() => props.entity.source === 'reused')
const isSuggested = computed(() => props.entity.source === 'ai_suggested')

const typeLabel = computed(() => {
  const found = ENTITY_TYPES.find((e) => e.name === props.entity.type)
  return found ? found.label_zh : props.entity.type
})
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
    </div>
    <div v-if="entity.description || (isSuggested && entity.confidence != null)" class="entity-chip__meta">
      <span v-if="entity.description">{{ entity.description }}</span>
      <span v-if="isSuggested && entity.confidence != null" class="entity-chip__conf">
        置信度 {{ (entity.confidence * 100).toFixed(0) }}%
      </span>
    </div>
    <div class="entity-chip__actions">
      <template v-if="isSuggested">
        <button class="entity-chip__btn entity-chip__btn--accept" @click="$emit('accept', entity.id)">✓</button>
        <button class="entity-chip__btn entity-chip__btn--reject" @click="$emit('reject', entity.id)">✕</button>
      </template>
      <template v-else>
        <button class="entity-chip__btn entity-chip__btn--delete" @click="$emit('delete', entity.id)">×</button>
      </template>
    </div>
  </div>
</template>
