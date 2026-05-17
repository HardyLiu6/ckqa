<script setup>
import { computed, ref, watch } from 'vue'
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

const emit = defineEmits([
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
  // 批量操作（spec § 风险 #1：禁止"一键全采纳"，但允许"用户主动勾选后批量采纳/拒绝"）
  'accept-selected-entities',
  'reject-selected-entities',
  'accept-selected-relations',
  'reject-selected-relations',
  'delete-selected-entities',
  'delete-selected-relations',
])

const showEntityEditor = ref(false)
const showRelationEditor = ref(false)

// 编辑器预填数据：完整对象，新建/编辑场景共用
// 新建：null（编辑器内部 fallback 到默认值或 prefilledName/prefilledSpan）
// 编辑：完整 entity / relation 对象
const entityEditorPrefill = ref(null)
const relationEditorPrefill = ref(null)

// 编辑模式标志：true 时编辑器按钮显示"保存修改"，提交时主组件做"拒绝原候选 + 新建"
const entityEditorMode = ref('create') // 'create' | 'edit-suggestion'
const relationEditorMode = ref('create') // 'create' | 'edit-suggestion'

// 批量选择状态：候选区 / 已确认区独立维护
// Set<string> 存被勾选的 id；切样本时由 watch 清空
const selectedSuggestedEntityIds = ref(new Set())
const selectedSuggestedRelationIds = ref(new Set())
const selectedConfirmedEntityIds = ref(new Set())
const selectedConfirmedRelationIds = ref(new Set())

// AI 候选编辑：用户点 ✎ 按钮 → 唤起 EntityEditor/RelationEditor 预填，
// 提交后等价于"拒绝原候选 + 新建"。这里只处理唤起，提交逻辑在父组件 handleEdit*
const editingSuggestedEntityId = ref(null)
const editingSuggestedRelationId = ref(null)

watch(() => props.sample?.id, () => {
  // 切样本时清空所有选择，避免跨样本误操作
  selectedSuggestedEntityIds.value = new Set()
  selectedSuggestedRelationIds.value = new Set()
  selectedConfirmedEntityIds.value = new Set()
  selectedConfirmedRelationIds.value = new Set()
  editingSuggestedEntityId.value = null
  editingSuggestedRelationId.value = null
})

const mergedEntities = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldEntities.map((e) => ({ ...e }))
  const suggested = props.sample.aiSuggestedEntities.map((e) => ({ ...e, source: 'ai_suggested' }))
  return [...confirmed, ...suggested]
})

const mergedRelations = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldRelations.map((r) => ({ ...r }))
  // AI 候选关系：强制写 source='ai_suggested'，与实体保持一致语义
  // 让 AnnotationRelationCard.isSuggested 能正确分支到"AI 候选"渲染路径
  const suggested = (props.sample.aiSuggestedRelations ?? [])
    .map((r) => ({ ...r, source: 'ai_suggested' }))
  return [...confirmed, ...suggested]
})

const entityMap = computed(() => {
  const m = {}
  for (const e of mergedEntities.value) m[e.id] = e
  return m
})

// 拆分已确认 / 候选两组，分组渲染时各自带工具栏
const confirmedEntities = computed(() => props.sample?.goldEntities ?? [])
const aiEntities = computed(() => props.sample?.aiSuggestedEntities ?? [])
const confirmedRelations = computed(() => props.sample?.goldRelations ?? [])
const aiRelations = computed(() => props.sample?.aiSuggestedRelations ?? [])

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

const entityEditorPrefillName = ref('')
const entityEditorPrefillSpan = ref(null)

// ─── 多选切换 ─────────────────────────────────────────────────────────
//
// 这些函数接收 Set 实例（模板自动 unwrap ref），直接调用 add/delete/clear。
// Vue 3 的 reactive Set 对这些原生方法都有 reactivity trap，会正确触发模板更新；
// 不要写 `set.value = new Set(...)`：`set` 在模板调用语境里已经是 Set 实例本体，
// 写 .value 只会在 Set 上附加一个无效属性，原 ref 完全不会动，UI 会"点了没反应"。

function toggleSelected(set, id) {
  if (set.has(id)) set.delete(id)
  else set.add(id)
}

function selectAll(set, ids) {
  set.clear()
  for (const id of ids) set.add(id)
}

function clearSelection(set) {
  set.clear()
}

function handleRequestAddEntity({ name, spanStart, spanEnd }) {
  // 拖选场景：纯新建，只预填 name + span
  entityEditorPrefill.value = null
  entityEditorPrefillName.value = name
  entityEditorPrefillSpan.value = { spanStart, spanEnd }
  entityEditorMode.value = 'create'
  editingSuggestedEntityId.value = null
  showEntityEditor.value = true
}

function openCreateEntityDialog() {
  // 纯新建（顶部"+ 添加实体"按钮）
  entityEditorPrefill.value = null
  entityEditorPrefillName.value = ''
  entityEditorPrefillSpan.value = null
  entityEditorMode.value = 'create'
  editingSuggestedEntityId.value = null
  showEntityEditor.value = true
}

function openCreateRelationDialog() {
  relationEditorPrefill.value = null
  relationEditorMode.value = 'create'
  editingSuggestedRelationId.value = null
  showRelationEditor.value = true
}

// ─── 编辑 AI 候选：唤起 Editor 完整预填 ────────────────────────────────────

function handleEditSuggestedEntity(entityId) {
  const entity = props.sample?.aiSuggestedEntities?.find((e) => e.id === entityId)
  if (!entity) return
  // 编辑场景：把候选完整 spread 到 prefill，type/description/spanStart/spanEnd 一并预填
  // 用户改完点'保存修改' → 父组件做'拒绝原候选 + 新建带修改的实体'
  entityEditorPrefill.value = { ...entity }
  entityEditorPrefillName.value = ''
  entityEditorPrefillSpan.value = null
  entityEditorMode.value = 'edit-suggestion'
  editingSuggestedEntityId.value = entityId
  showEntityEditor.value = true
}

function handleEditSuggestedRelation(relationId) {
  const relation = props.sample?.aiSuggestedRelations?.find((r) => r.id === relationId)
  if (!relation) return
  // 关系候选预填：把 originalSource/originalTarget（实体名）解析成 entityId
  // 已采纳的同名实体 → 取其 id；未采纳 → 留空让用户手选
  const sourceEntityId = relation.sourceEntityId
    ?? confirmedEntities.value.find((e) => e.name === relation.originalSource)?.id
    ?? ''
  const targetEntityId = relation.targetEntityId
    ?? confirmedEntities.value.find((e) => e.name === relation.originalTarget)?.id
    ?? ''
  relationEditorPrefill.value = {
    sourceEntityId,
    targetEntityId,
    type: relation.type ?? '',
    evidence: relation.evidence ?? '',
  }
  relationEditorMode.value = 'edit-suggestion'
  editingSuggestedRelationId.value = relationId
  showRelationEditor.value = true
}

// ─── Dialog 的 submit / close 收口 ──────────────────────────────────

function handleEntityEditorSubmit(payload) {
  if (entityEditorMode.value === 'edit-suggestion' && editingSuggestedEntityId.value) {
    // 编辑 AI 候选：把原候选拒绝 + 新建带修改的实体
    const oldId = editingSuggestedEntityId.value
    emit('reject-entity', oldId)
    emit('create-entity', payload)
  } else {
    emit('create-entity', payload)
  }
  closeEntityEditor()
}

function closeEntityEditor() {
  showEntityEditor.value = false
  entityEditorPrefill.value = null
  entityEditorPrefillName.value = ''
  entityEditorPrefillSpan.value = null
  entityEditorMode.value = 'create'
  editingSuggestedEntityId.value = null
}

function handleRelationEditorSubmit(payload) {
  if (relationEditorMode.value === 'edit-suggestion' && editingSuggestedRelationId.value) {
    const oldId = editingSuggestedRelationId.value
    emit('reject-relation', oldId)
    emit('create-relation', payload)
  } else {
    emit('create-relation', payload)
  }
  closeRelationEditor()
}

function closeRelationEditor() {
  showRelationEditor.value = false
  relationEditorPrefill.value = null
  relationEditorMode.value = 'create'
  editingSuggestedRelationId.value = null
}
</script>

<template>
  <main class="annotation-work-area">
    <div v-if="!sample" class="annotation-work-area__empty">
      <p class="ann-text-muted">请在左侧选择一条样本开始标注</p>
    </div>
    <template v-else>
      <!-- 标题工作栏：卡片化，左侧元信息 + 右侧工具栏（跳过/完成） -->
      <header class="annotation-work-area__head annotation-work-area__head--card">
        <div class="annotation-work-area__meta">
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
          <button class="annotation-section-title__add" @click="openCreateEntityDialog">
            + 添加实体
          </button>
        </header>

        <!-- 已确认实体子区 + 批量删除工具栏（仅在选中时显示） -->
        <div v-if="confirmedEntities.length > 0" class="annotation-subgroup">
          <div v-if="selectedConfirmedEntityIds.size > 0" class="annotation-subgroup__toolbar">
            <span class="ann-text-muted">已选 {{ selectedConfirmedEntityIds.size }} 个</span>
            <button class="ann-btn ann-btn--reject" @click="$emit('delete-selected-entities', Array.from(selectedConfirmedEntityIds)); clearSelection(selectedConfirmedEntityIds)">批量删除</button>
            <button class="ann-btn ann-btn--soft" @click="clearSelection(selectedConfirmedEntityIds)">取消</button>
          </div>
          <div class="entity-chip-grid">
            <div
              v-for="entity in confirmedEntities"
              :key="`c:${entity.id}`"
              class="entity-chip-cell"
            >
              <input
                type="checkbox"
                class="entity-chip-cell__check"
                :checked="selectedConfirmedEntityIds.has(entity.id)"
                @change="toggleSelected(selectedConfirmedEntityIds, entity.id)"
                aria-label="选中"
              >
              <AnnotationEntityCard
                :entity="entity"
                @delete="$emit('delete-entity', $event)"
              />
            </div>
          </div>
        </div>

        <!-- AI 候选实体子区 + 批量采纳/拒绝工具栏 -->
        <div v-if="aiEntities.length > 0" class="annotation-subgroup annotation-subgroup--ai">
          <div class="annotation-subgroup__toolbar">
            <span class="annotation-subgroup__label">
              <span class="annotation-subgroup__icon">✨</span>
              AI 候选 · {{ aiEntities.length }}
              <span v-if="selectedSuggestedEntityIds.size > 0" class="ann-text-muted">（已选 {{ selectedSuggestedEntityIds.size }}）</span>
            </span>
            <button
              class="ann-btn ann-btn--soft"
              :disabled="selectedSuggestedEntityIds.size === aiEntities.length"
              @click="selectAll(selectedSuggestedEntityIds, aiEntities.map((e) => e.id))"
            >全选</button>
            <button
              class="ann-btn ann-btn--soft"
              :disabled="selectedSuggestedEntityIds.size === 0"
              @click="clearSelection(selectedSuggestedEntityIds)"
            >取消</button>
            <button
              class="ann-btn ann-btn--accept"
              :disabled="selectedSuggestedEntityIds.size === 0"
              @click="$emit('accept-selected-entities', Array.from(selectedSuggestedEntityIds)); clearSelection(selectedSuggestedEntityIds)"
            >采纳所选 {{ selectedSuggestedEntityIds.size > 0 ? `(${selectedSuggestedEntityIds.size})` : '' }}</button>
            <button
              class="ann-btn ann-btn--reject"
              :disabled="selectedSuggestedEntityIds.size === 0"
              @click="$emit('reject-selected-entities', Array.from(selectedSuggestedEntityIds)); clearSelection(selectedSuggestedEntityIds)"
            >拒绝所选 {{ selectedSuggestedEntityIds.size > 0 ? `(${selectedSuggestedEntityIds.size})` : '' }}</button>
          </div>
          <div class="entity-chip-grid">
            <div
              v-for="entity in aiEntities"
              :key="`s:${entity.id}`"
              class="entity-chip-cell"
            >
              <input
                type="checkbox"
                class="entity-chip-cell__check"
                :checked="selectedSuggestedEntityIds.has(entity.id)"
                @change="toggleSelected(selectedSuggestedEntityIds, entity.id)"
                aria-label="选中候选"
              >
              <AnnotationEntityCard
                :entity="{ ...entity, source: 'ai_suggested' }"
                @accept="$emit('accept-entity', $event)"
                @reject="$emit('reject-entity', $event)"
                @edit="handleEditSuggestedEntity"
              />
            </div>
          </div>
        </div>

        <!-- EntityEditor 在 el-dialog 中渲染（见模板末尾），不再以行内方式插在卡片下方 -->
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
            @click="openCreateRelationDialog"
          >
            + 添加关系
          </button>
        </header>

        <!-- 已确认关系子区 + 批量删除 -->
        <div v-if="confirmedRelations.length > 0" class="annotation-subgroup">
          <div v-if="selectedConfirmedRelationIds.size > 0" class="annotation-subgroup__toolbar">
            <span class="ann-text-muted">已选 {{ selectedConfirmedRelationIds.size }} 个</span>
            <button class="ann-btn ann-btn--reject" @click="$emit('delete-selected-relations', Array.from(selectedConfirmedRelationIds)); clearSelection(selectedConfirmedRelationIds)">批量删除</button>
            <button class="ann-btn ann-btn--soft" @click="clearSelection(selectedConfirmedRelationIds)">取消</button>
          </div>
          <div class="annotation-relation-grid">
            <div
              v-for="relation in confirmedRelations"
              :key="`c:${relation.id}`"
              class="annotation-relation-cell"
            >
              <input
                type="checkbox"
                class="annotation-relation-cell__check"
                :checked="selectedConfirmedRelationIds.has(relation.id)"
                @change="toggleSelected(selectedConfirmedRelationIds, relation.id)"
                aria-label="选中"
              >
              <AnnotationRelationCard
                :relation="relation"
                :entity-map="entityMap"
                @delete="$emit('delete-relation', $event)"
              />
            </div>
          </div>
        </div>

        <!-- AI 候选关系子区 + 批量采纳/拒绝 -->
        <div v-if="aiRelations.length > 0" class="annotation-subgroup annotation-subgroup--ai">
          <div class="annotation-subgroup__toolbar">
            <span class="annotation-subgroup__label">
              <span class="annotation-subgroup__icon">✨</span>
              AI 候选 · {{ aiRelations.length }}
              <span v-if="selectedSuggestedRelationIds.size > 0" class="ann-text-muted">（已选 {{ selectedSuggestedRelationIds.size }}）</span>
            </span>
            <button
              class="ann-btn ann-btn--soft"
              :disabled="selectedSuggestedRelationIds.size === aiRelations.length"
              @click="selectAll(selectedSuggestedRelationIds, aiRelations.map((r) => r.id))"
            >全选</button>
            <button
              class="ann-btn ann-btn--soft"
              :disabled="selectedSuggestedRelationIds.size === 0"
              @click="clearSelection(selectedSuggestedRelationIds)"
            >取消</button>
            <button
              class="ann-btn ann-btn--accept"
              :disabled="selectedSuggestedRelationIds.size === 0"
              @click="$emit('accept-selected-relations', Array.from(selectedSuggestedRelationIds)); clearSelection(selectedSuggestedRelationIds)"
            >采纳所选 {{ selectedSuggestedRelationIds.size > 0 ? `(${selectedSuggestedRelationIds.size})` : '' }}</button>
            <button
              class="ann-btn ann-btn--reject"
              :disabled="selectedSuggestedRelationIds.size === 0"
              @click="$emit('reject-selected-relations', Array.from(selectedSuggestedRelationIds)); clearSelection(selectedSuggestedRelationIds)"
            >拒绝所选 {{ selectedSuggestedRelationIds.size > 0 ? `(${selectedSuggestedRelationIds.size})` : '' }}</button>
          </div>
          <div class="annotation-relation-grid">
            <div
              v-for="relation in aiRelations"
              :key="`s:${relation.id}`"
              class="annotation-relation-cell"
            >
              <input
                type="checkbox"
                class="annotation-relation-cell__check"
                :checked="selectedSuggestedRelationIds.has(relation.id)"
                @change="toggleSelected(selectedSuggestedRelationIds, relation.id)"
                aria-label="选中候选"
              >
              <AnnotationRelationCard
                :relation="{ ...relation, source: 'ai_suggested' }"
                :entity-map="entityMap"
                @accept="$emit('accept-relation', $event)"
                @reject="$emit('reject-relation', $event)"
                @edit="handleEditSuggestedRelation"
              />
            </div>
          </div>
        </div>

        <!-- RelationEditor 在 el-dialog 中渲染（见模板末尾） -->
      </section>
    </template>
  </main>

  <!-- 实体编辑器对话框：新建 + 编辑共用 -->
  <el-dialog
    v-model="showEntityEditor"
    :title="entityEditorMode === 'edit-suggestion' ? '编辑 AI 候选实体' : '添加实体'"
    width="640px"
    :close-on-click-modal="false"
    @close="closeEntityEditor"
  >
    <EntityEditor
      v-if="showEntityEditor"
      :existing-entities="mergedEntities"
      :prefilled-entity="entityEditorPrefill"
      :prefilled-name="entityEditorPrefillName"
      :prefilled-span="entityEditorPrefillSpan"
      :edit-mode="entityEditorMode === 'edit-suggestion'"
      @submit="handleEntityEditorSubmit"
      @cancel="closeEntityEditor"
    />
  </el-dialog>

  <!-- 关系编辑器对话框：新建 + 编辑共用 -->
  <el-dialog
    v-model="showRelationEditor"
    :title="relationEditorMode === 'edit-suggestion' ? '编辑 AI 候选关系' : '添加关系'"
    width="640px"
    :close-on-click-modal="false"
    @close="closeRelationEditor"
  >
    <RelationEditor
      v-if="showRelationEditor"
      :entities="sample?.goldEntities ?? []"
      :prefilled-relation="relationEditorPrefill"
      :edit-mode="relationEditorMode === 'edit-suggestion'"
      @submit="handleRelationEditorSubmit"
      @cancel="closeRelationEditor"
    />
  </el-dialog>
</template>
