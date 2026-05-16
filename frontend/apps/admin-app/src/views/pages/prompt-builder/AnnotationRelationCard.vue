<script setup>
import { computed } from 'vue'
import { describeRelationType } from './relation-types-model.js'

const props = defineProps({
  relation: { type: Object, required: true },
  /** 实体 id → 实体对象 的映射，用于把 sourceEntityId / targetEntityId 渲染为名字 */
  entityMap: { type: Object, required: true },
})

defineEmits(['accept', 'reject', 'delete', 'edit'])

const isSuggested = computed(() => props.relation.source === 'ai_suggested' || props.relation.source === 'ai_schema_inferred')

// 解析两端实体名：
// 1. 已确认关系：通过 sourceEntityId / targetEntityId 查 entityMap
// 2. AI 候选：可能用 originalSource / originalTarget（GraphRAG 输出的实体名字符串）
//    - 命中 entityMap 内同名实体 → 显示名 + 已关联视觉
//    - 未命中 → 直接显示原始名 + "未关联"灰底标识，提醒用户先采纳两端实体
function resolveEntityName(idOrName, fallback) {
  if (idOrName && props.entityMap[idOrName]) {
    return { name: props.entityMap[idOrName].name, linked: true }
  }
  if (fallback) {
    // AI 候选场景：fallback 是实体名字符串，直接用
    return { name: fallback, linked: false }
  }
  return { name: '?', linked: false }
}

const sourceInfo = computed(() => resolveEntityName(props.relation.sourceEntityId, props.relation.originalSource))
const targetInfo = computed(() => resolveEntityName(props.relation.targetEntityId, props.relation.originalTarget))

const typeLabelZh = computed(() => {
  const desc = describeRelationType(props.relation.type)
  return desc ? `${props.relation.type}（${desc.label_zh}）` : (props.relation.type || '?')
})
</script>

<template>
  <div
    class="annotation-relation-card"
    :class="{ 'is-suggested': isSuggested }"
  >
    <span v-if="isSuggested" class="annotation-relation-card__badge" aria-hidden="true">✨</span>
    <div class="annotation-relation-card__main">
      <div class="annotation-relation-card__flow">
        <span
          class="annotation-relation-card__entity"
          :class="{ 'is-unlinked': !sourceInfo.linked }"
          :title="sourceInfo.linked ? '' : '尚未采纳此实体'"
        >{{ sourceInfo.name }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span class="annotation-relation-card__type-tag">{{ typeLabelZh }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span
          class="annotation-relation-card__entity"
          :class="{ 'is-unlinked': !targetInfo.linked }"
          :title="targetInfo.linked ? '' : '尚未采纳此实体'"
        >{{ targetInfo.name }}</span>
      </div>
      <p v-if="relation.evidence" class="annotation-relation-card__evidence">
        证据：{{ relation.evidence }}
      </p>
    </div>
    <div class="annotation-relation-card__actions">
      <template v-if="isSuggested">
        <button class="ann-btn ann-btn--icon" title="编辑后采纳" @click="$emit('edit', relation.id)">✎</button>
        <button class="ann-btn ann-btn--accept" @click="$emit('accept', relation.id)">采纳</button>
        <button class="ann-btn ann-btn--reject" @click="$emit('reject', relation.id)">拒绝</button>
      </template>
      <template v-else>
        <button class="ann-btn ann-btn--icon" title="删除" @click="$emit('delete', relation.id)">×</button>
      </template>
    </div>
  </div>
</template>
