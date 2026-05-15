<script setup>
import { computed } from 'vue'
import AnnotationEntityCard from './AnnotationEntityCard.vue'
import AnnotationRelationCard from './AnnotationRelationCard.vue'

const props = defineProps({
  sample: { type: Object, default: null },
})

defineEmits([
  'finish-sample',
  'skip-sample',
  'accept-entity',
  'reject-entity',
  'delete-entity',
  'accept-relation',
  'reject-relation',
  'delete-relation',
  'sort-suggestions-by-confidence',
])

const mergedEntities = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldEntities.map((e) => ({ ...e }))
  const suggested = props.sample.aiSuggestedEntities.map((e) => ({ ...e, source: 'ai_suggested' }))
  return [...confirmed, ...suggested]
})

const mergedRelations = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldRelations.map((r) => ({ ...r }))
  const suggested = (props.sample.aiSuggestedRelations ?? []).map((r) => ({ ...r }))
  return [...confirmed, ...suggested]
})

const entityMap = computed(() => {
  const m = {}
  for (const e of mergedEntities.value) m[e.id] = e
  return m
})

const confirmedCount = computed(() => props.sample?.goldEntities.length ?? 0)
const aiCount = computed(() => props.sample?.aiSuggestedEntities.length ?? 0)
const relConfirmedCount = computed(() => props.sample?.goldRelations.length ?? 0)
const relAiCount = computed(() => (props.sample?.aiSuggestedRelations ?? []).length)

const breadcrumb = computed(() => {
  if (!props.sample?.headingPath) return ''
  return props.sample.headingPath.join(' / ')
})

function priorityLabel(p) {
  return ({ high: '高', medium: '中', low: '低' })[p] ?? p
}

function signalLabel(name) {
  return ({
    definition_signal: '定义信号',
    formula_signal:    '公式信号',
    method_signal:     '方法/步骤信号',
    experiment_signal: '实验信号',
    assignment_signal: '作业信号',
  })[name] ?? name
}
</script>

<template>
  <main class="annotation-work-area">
    <div v-if="!sample" class="annotation-work-area__empty">
      <p class="ann-text-muted">请在左侧选择一条样本开始标注</p>
    </div>
    <template v-else>
      <header class="annotation-work-area__head">
        <div>
          <div class="annotation-work-area__title-row">
            <h3>{{ sample.headingPath?.[sample.headingPath.length - 1] ?? '(无标题)' }}</h3>
            <span class="ann-pill" :class="`ann-pill--${sample.auditPriority}`">
              {{ priorityLabel(sample.auditPriority) }}
            </span>
          </div>
          <div class="annotation-work-area__breadcrumb">
            <code>{{ sample.id }}</code> · {{ breadcrumb }} · 第 {{ sample.pageStart }}{{ sample.pageStart !== sample.pageEnd ? `-${sample.pageEnd}` : '' }} 页
          </div>
          <div class="annotation-work-area__signals">
            <span v-for="sig in sample.hitSignals" :key="sig" class="ann-pill ann-pill--soft">
              {{ signalLabel(sig) }}
            </span>
          </div>
        </div>
        <div class="annotation-work-area__actions">
          <el-button @click="$emit('skip-sample', sample.id)">跳过</el-button>
          <el-button type="primary" @click="$emit('finish-sample', sample.id)">完成 ✓</el-button>
        </div>
      </header>

      <!-- D 智能：历史复用横幅 -->
      <div v-if="sample.reusedFrom" class="annotation-banner annotation-banner--reuse">
        <span class="annotation-banner__icon">♻</span>
        <div class="annotation-banner__text">
          发现来自
          <strong>{{ sample.reusedFrom.buildRunName }}</strong>
          的标注，已为你预填。
        </div>
      </div>

      <!-- A 智能：AI 预填横幅 -->
      <div v-if="aiCount > 0" class="annotation-banner annotation-banner--ai">
        <span class="annotation-banner__icon">✨</span>
        <div class="annotation-banner__text">
          AI 助手已生成 <strong>{{ aiCount }} 个候选实体</strong>，请逐条审阅。
        </div>
        <div class="annotation-banner__actions">
          <button class="ann-btn ann-btn--soft" @click="$emit('sort-suggestions-by-confidence')">按置信度排序</button>
        </div>
      </div>

      <!-- 原文卡 -->
      <article class="annotation-text-card">
        <header class="annotation-text-card__head">
          <span class="ann-text-tiny">原文</span>
        </header>
        <div class="annotation-text-card__body">
          {{ sample.text }}
        </div>
      </article>

      <!-- 实体区 -->
      <section>
        <header class="annotation-section-title">
          <strong>实体</strong>
          <span class="ann-text-muted">{{ confirmedCount }} 已确认 · {{ aiCount }} 待审</span>
        </header>
        <div class="entity-chip-grid">
          <AnnotationEntityCard
            v-for="entity in mergedEntities"
            :key="`${entity.source}:${entity.id}`"
            :entity="entity"
            @accept="$emit('accept-entity', $event)"
            @reject="$emit('reject-entity', $event)"
            @delete="$emit('delete-entity', $event)"
          />
          <button class="entity-chip-grid__add">+ 添加实体</button>
        </div>
      </section>

      <!-- 关系区 -->
      <section>
        <header class="annotation-section-title">
          <strong>关系</strong>
          <span class="ann-text-muted">{{ relConfirmedCount }} 已确认 · {{ relAiCount }} 待审</span>
          <span class="ann-text-tiny ann-text-tiny--accent annotation-section-title__hint-right">仅显示 schema 合法关系</span>
        </header>
        <div class="annotation-list">
          <AnnotationRelationCard
            v-for="relation in mergedRelations"
            :key="`${relation.source}:${relation.id}`"
            :relation="relation"
            :entity-map="entityMap"
            @accept="$emit('accept-relation', $event)"
            @reject="$emit('reject-relation', $event)"
            @delete="$emit('delete-relation', $event)"
          />
          <button class="annotation-add-row">+ 手动添加关系</button>
        </div>
      </section>
    </template>
  </main>
</template>
