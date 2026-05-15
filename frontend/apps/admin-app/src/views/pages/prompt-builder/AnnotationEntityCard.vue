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
  return found ? `${found.name}（${found.label_zh}）` : props.entity.type
})
</script>

<template>
  <div
    class="annotation-entity-card"
    :class="{
      'is-reused': isReused,
      'is-suggested': isSuggested,
    }"
  >
    <span class="annotation-entity-card__icon" aria-hidden="true">
      <template v-if="isReused">♻</template>
      <template v-else-if="isSuggested">✨</template>
    </span>
    <div class="annotation-entity-card__main">
      <div class="annotation-entity-card__row">
        <span class="annotation-entity-card__name">{{ entity.name }}</span>
        <span class="annotation-entity-card__type-tag">{{ typeLabel }}</span>
      </div>
      <p v-if="entity.description" class="annotation-entity-card__desc">
        {{ entity.description }}
        <small v-if="isSuggested && entity.confidence != null" class="annotation-entity-card__conf">
          · 置信度 {{ entity.confidence.toFixed(2) }}
        </small>
      </p>
    </div>
    <div class="annotation-entity-card__actions">
      <template v-if="isSuggested">
        <button class="ann-btn ann-btn--accept" @click="$emit('accept', entity.id)">采纳</button>
        <button class="ann-btn ann-btn--reject" @click="$emit('reject', entity.id)">拒绝</button>
      </template>
      <template v-else>
        <button class="ann-btn ann-btn--icon" title="删除" @click="$emit('delete', entity.id)">×</button>
      </template>
    </div>
  </div>
</template>
