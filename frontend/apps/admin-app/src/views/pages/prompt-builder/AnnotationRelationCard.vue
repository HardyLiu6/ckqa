<script setup>
import { computed } from 'vue'
import { describeRelationType } from './relation-types-model.js'

const props = defineProps({
  relation: { type: Object, required: true },
  /** 实体 id → 实体对象 的映射，用于把 sourceEntityId / targetEntityId 渲染为名字 */
  entityMap: { type: Object, required: true },
})

defineEmits(['accept', 'reject', 'delete'])

const isSuggested = computed(() => props.relation.source === 'ai_schema_inferred' || props.relation.source === 'ai_suggested')

const sourceName = computed(() => props.entityMap[props.relation.sourceEntityId]?.name ?? '?')
const targetName = computed(() => props.entityMap[props.relation.targetEntityId]?.name ?? '?')

const typeLabelZh = computed(() => {
  const desc = describeRelationType(props.relation.type)
  return desc ? `${props.relation.type}（${desc.label_zh}）` : props.relation.type
})
</script>

<template>
  <div
    class="annotation-relation-card"
    :class="{ 'is-suggested': isSuggested }"
  >
    <div class="annotation-relation-card__main">
      <div class="annotation-relation-card__flow">
        <span class="annotation-relation-card__entity">{{ sourceName }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span class="annotation-relation-card__type-tag">{{ typeLabelZh }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span class="annotation-relation-card__entity">{{ targetName }}</span>
      </div>
      <p v-if="relation.evidence" class="annotation-relation-card__evidence">
        证据：{{ relation.evidence }}
      </p>
    </div>
    <div class="annotation-relation-card__actions">
      <template v-if="isSuggested">
        <button class="ann-btn ann-btn--accept" @click="$emit('accept', relation.id)">采纳</button>
        <button class="ann-btn ann-btn--reject" @click="$emit('reject', relation.id)">拒绝</button>
      </template>
      <template v-else>
        <button class="ann-btn ann-btn--icon" title="删除" @click="$emit('delete', relation.id)">×</button>
      </template>
    </div>
  </div>
</template>
