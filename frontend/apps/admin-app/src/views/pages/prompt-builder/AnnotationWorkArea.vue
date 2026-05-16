<script setup>
import { computed, ref } from 'vue'
import AnnotationEntityCard from './AnnotationEntityCard.vue'
import AnnotationRelationCard from './AnnotationRelationCard.vue'
import EntityEditor from './EntityEditor.vue'
import RelationEditor from './RelationEditor.vue'
import AnnotationTextCard from './AnnotationTextCard.vue'

const props = defineProps({
  sample: { type: Object, default: null },
  /** 当前正在生成 AI 候选的 sampleId（null 表示无任务）。从 PromptBuilderPrepareStep 传入。 */
  aiSuggestionLoadingSampleId: { type: [String, Number], default: null },
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
  'create-entity',
  'create-relation',
  'request-ai-suggestions',
  'dismiss-reused-from',
])

const showEntityEditor = ref(false)
const showRelationEditor = ref(false)

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

// AI 候选三态：当前 sample 的 loading 状态判定
const isAiLoading = computed(() =>
  props.sample != null && props.aiSuggestionLoadingSampleId === props.sample.id
)

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

const entityEditorPrefill = ref({ name: '', span: null })

function handleRequestAddEntity({ name, spanStart, spanEnd }) {
  entityEditorPrefill.value = { name, span: { spanStart, spanEnd } }
  showEntityEditor.value = true
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
        <div class="annotation-banner__actions">
          <button class="ann-btn ann-btn--soft" @click="$emit('dismiss-reused-from', sample.id)">
            隐藏复用提示
          </button>
        </div>
      </div>

      <!-- A 智能：AI 预填横幅 -->
      <!-- 三态机：
           1. 加载中（isAiLoading）：显示 spinner + "AI 候选生成中..."，隐藏所有按钮
           2. 已生成（aiCount > 0 且 非 loading）：显示候选数 + "按置信度排序" + "重新生成"
           3. 未生成（aiCount === 0 且 非 loading）：显示生成提示 + "生成候选" 按钮 -->
      <div class="annotation-banner annotation-banner--ai">
        <span class="annotation-banner__icon">✨</span>
        <template v-if="isAiLoading">
          <div class="annotation-banner__text">
            <span class="annotation-banner__spinner" aria-hidden="true"></span>
            AI 候选生成中，约 1-2 分钟，请稍候...
          </div>
        </template>
        <template v-else-if="aiCount > 0">
          <div class="annotation-banner__text">
            AI 助手已生成 <strong>{{ aiCount }} 个候选实体</strong>，请逐条审阅。
          </div>
          <div class="annotation-banner__actions">
            <button class="ann-btn ann-btn--soft" @click="$emit('sort-suggestions-by-confidence')">按置信度排序</button>
            <button class="ann-btn ann-btn--soft" @click="$emit('request-ai-suggestions', sample.id)">重新生成</button>
          </div>
        </template>
        <template v-else>
          <div class="annotation-banner__text">
            可用 AI 助手抽取一遍，作为标注起点（约 1-2 分钟，所有候选都需逐条审阅）。
          </div>
          <div class="annotation-banner__actions">
            <button class="ann-btn ann-btn--accent" @click="$emit('request-ai-suggestions', sample.id)">
              生成候选
            </button>
          </div>
        </template>
      </div>

      <!-- 原文卡（含选区监听 + 实体高亮） -->
      <AnnotationTextCard
        :text="sample.text"
        :entities="sample.goldEntities"
        @request-add-entity="handleRequestAddEntity"
      />

      <!-- 实体区 -->
      <section>
        <header class="annotation-section-title">
          <strong>实体</strong>
          <span class="annotation-section-title__count">{{ confirmedCount }} 已确认 · {{ aiCount }} 待审</span>
          <button class="annotation-section-title__add" @click="showEntityEditor = !showEntityEditor">
            {{ showEntityEditor ? '收起 −' : '+ 添加实体' }}
          </button>
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
        </div>
        <EntityEditor
          v-if="showEntityEditor"
          :existing-entities="mergedEntities"
          :prefilled-name="entityEditorPrefill.name"
          :prefilled-span="entityEditorPrefill.span"
          @submit="(payload) => {
            $emit('create-entity', payload)
            showEntityEditor = false
            entityEditorPrefill = { name: '', span: null }
          }"
          @cancel="() => {
            showEntityEditor = false
            entityEditorPrefill = { name: '', span: null }
          }"
        />
      </section>

      <!-- 关系区 -->
      <section>
        <header class="annotation-section-title">
          <strong>关系</strong>
          <span class="annotation-section-title__count">{{ relConfirmedCount }} 已确认 · {{ relAiCount }} 待审</span>
          <span class="ann-text-tiny ann-text-tiny--accent annotation-section-title__hint-right">仅显示 schema 合法关系</span>
          <button
            class="annotation-section-title__add"
            :disabled="(sample?.goldEntities ?? []).length < 2"
            :title="(sample?.goldEntities ?? []).length < 2 ? '至少需要 2 个已确认实体才能添加关系' : ''"
            @click="showRelationEditor = !showRelationEditor"
          >
            {{ showRelationEditor ? '收起 −' : '+ 添加关系' }}
          </button>
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
        </div>
        <RelationEditor
          v-if="showRelationEditor"
          :entities="sample?.goldEntities ?? []"
          @submit="(payload) => { $emit('create-relation', payload); showRelationEditor = false }"
          @cancel="showRelationEditor = false"
        />
      </section>
    </template>
  </main>
</template>
