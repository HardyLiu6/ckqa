<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkInfoTable from '../../components/common/CkInfoTable.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import {
  activateIndexRun,
  getIndexRun,
  listIndexRunArtifacts,
} from '../../api/knowledge-bases.js'

import BuildRunLivePanel from './components/BuildRunLivePanel.vue'
import { KB_PAGE_COPY } from './kb-page-copy.js'

// 索引版本详情：只读版本的"右侧实时面板 + 摘要"
// 数据来源：getIndexRun(indexRunId) + listIndexRunArtifacts(indexRunId)

const route = useRoute()

const indexRunId = computed(() => route.params.indexRunId)
const state = ref({ loading: true, error: null, run: null, artifacts: [] })
const feedback = ref({ message: '', tone: '' })

async function load() {
  state.value.loading = true
  try {
    const [run, artifacts] = await Promise.all([
      getIndexRun(indexRunId.value),
      listIndexRunArtifacts(indexRunId.value).catch(() => []),
    ])
    state.value = { loading: false, error: null, run, artifacts: Array.isArray(artifacts) ? artifacts : [] }
    // 面包屑上下文
    route.meta.contextChain = [
      { label: '索引版本', to: '/app/knowledge-bases' },
      { label: `#${indexRunId.value}` },
    ]
  } catch (error) {
    state.value = { loading: false, error, run: null, artifacts: [] }
  }
}

watch(indexRunId, load)
onMounted(load)

async function onActivate() {
  const run = state.value.run
  if (!run) return
  try {
    await activateIndexRun(run.knowledgeBaseId, run.id)
    feedback.value = { message: `已激活索引 #${run.id}`, tone: 'success' }
    load()
  } catch (error) {
    feedback.value = { message: error?.message ?? '激活失败，请稍后重试', tone: 'danger' }
  }
}

const heroTitle = computed(() => {
  const run = state.value.run
  if (!run) return `索引版本 #${indexRunId.value}`
  return `${run.indexVersion ?? `索引版本 #${run.id}`}`
})

const heroSubtitle = computed(() => {
  const run = state.value.run
  if (!run) return ''
  const kb = run.knowledgeBaseName ?? `知识库 #${run.knowledgeBaseId}`
  return `${kb} · 状态：${statusTone.value.label}`
})

const statusTone = computed(() => {
  const run = state.value.run
  const status = String(run?.status ?? '').toLowerCase()
  if (status === 'success') return { tone: 'success', label: '构建成功' }
  if (status === 'running') return { tone: 'running', label: '构建中' }
  if (status === 'failed') return { tone: 'danger', label: '构建失败' }
  return { tone: 'neutral', label: status || '未知' }
})

// InfoTable 条目
const infoEntries = computed(() => {
  const run = state.value.run
  if (!run) return []
  return [
    { label: '知识库 ID', value: run.knowledgeBaseId ?? '-' },
    { label: '索引版本', value: run.indexVersion ?? `#${run.id}` },
    { label: '当前状态', value: statusTone.value.label },
    { label: '是否激活', value: run.active ? '是' : '否' },
    { label: '开始时间', value: run.startedAt ?? run.createdAt ?? '-' },
    { label: '结束时间', value: run.finishedAt ?? '-' },
    { label: '启动人', value: run.operatorName ?? run.requestedBy ?? '-' },
    { label: '资料数量', value: run.materialCount ?? '-' },
  ]
})

// 把索引运行包装成 BuildRunLivePanel 可以消费的 stages（只读）
const readonlyTimeline = computed(() => {
  const run = state.value.run
  const status = String(run?.status ?? '').toLowerCase()
  if (!run) return []
  // 对于已结束的索引运行，所有阶段都 done；对 running 状态，最后一步显示 running
  return [
    { key: 'input', title: '输入准备', state: 'done', currentPct: 100 },
    { key: 'index', title: '构建索引', state: status === 'success' ? 'done' : (status === 'failed' ? 'failed' : 'running'), currentPct: status === 'success' ? 100 : 60 },
  ]
})

const canActivate = computed(() => {
  const run = state.value.run
  return run && String(run.status ?? '').toLowerCase() === 'success' && !run.active
})
</script>

<template>
  <div class="index-run-detail-page" data-testid="index-run-detail-page">
    <CkSkeleton v-if="state.loading" variant="card" :count="1" />

    <CkEmptyState
      v-else-if="state.error || !state.run"
      icon="!"
      title="索引版本加载失败"
      :description="state.error?.message || ''"
    />

    <template v-else>
      <CkPageHero :eyebrow="KB_PAGE_COPY.indexRunDetail.eyebrow" :title="heroTitle" :subtitle="heroSubtitle">
        <template #actions>
          <CkStatusPill :tone="statusTone.tone" :label="statusTone.label" />
          <button
            v-if="canActivate"
            type="button"
            class="index-run-detail-page__activate ck-pressable"
            data-testid="index-run-activate"
            @click="onActivate"
          >
            {{ KB_PAGE_COPY.indexRunDetail.activateCta }}
          </button>
        </template>
      </CkPageHero>

      <div
        v-if="feedback.message"
        class="index-run-detail-page__feedback"
        :data-tone="feedback.tone"
        role="status"
        aria-live="polite"
      >
        {{ feedback.message }}
      </div>

      <div class="index-run-detail-page__columns">
        <section class="index-run-detail-page__left">
          <h2 class="index-run-detail-page__heading">基础信息</h2>
          <CkInfoTable :entries="infoEntries" :columns="2" />

          <h2 class="index-run-detail-page__heading">{{ KB_PAGE_COPY.indexRunDetail.artifactsTitle }}</h2>
          <ul v-if="state.artifacts.length" class="index-run-detail-page__artifacts">
            <li v-for="artifact in state.artifacts" :key="artifact.id">
              <span class="index-run-detail-page__artifact-name">{{ artifact.fileName ?? artifact.path ?? `产物 ${artifact.id}` }}</span>
              <span class="index-run-detail-page__artifact-meta">{{ artifact.artifactType ?? '' }}</span>
            </li>
          </ul>
          <p v-else class="index-run-detail-page__muted">暂无产物记录</p>
        </section>

        <aside class="index-run-detail-page__right">
          <BuildRunLivePanel
            :status="state.run.status"
            :timeline="readonlyTimeline"
            :active-key="readonlyTimeline.find((s) => s.state === 'running')?.key ?? ''"
            :current-pct="readonlyTimeline.find((s) => s.state === 'running')?.currentPct ?? 0"
            :overall-pct="state.run.status === 'success' ? 100 : 60"
            :logs="[]"
            :failure-reason="state.run.failureReason ?? ''"
            :failed-stage-key="state.run.status === 'failed' ? 'index' : ''"
            :can-act="false"
            mode="snapshot"
            empty-hint="本版本的构建日志已归档，不再实时滚动"
          />
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.index-run-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.index-run-detail-page__activate {
  padding: 7px 14px;
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  border: 1px solid var(--ckqa-accent-strong);
  cursor: pointer;
}
.index-run-detail-page__activate:hover {
  background: var(--ckqa-accent-strong);
}
.index-run-detail-page__feedback {
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.index-run-detail-page__feedback[data-tone='success'] {
  background: var(--ckqa-success-soft);
  color: var(--ckqa-success);
}
.index-run-detail-page__feedback[data-tone='danger'] {
  background: var(--ckqa-danger-soft);
  color: var(--ckqa-danger);
}
.index-run-detail-page__columns {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: var(--ckqa-space-4);
  align-items: flex-start;
}
.index-run-detail-page__left {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
  min-width: 0;
}
.index-run-detail-page__heading {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-semibold);
}
.index-run-detail-page__artifacts {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.index-run-detail-page__artifacts li {
  padding: 8px 12px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  display: flex;
  justify-content: space-between;
  font-size: var(--ckqa-text-sm-size);
}
.index-run-detail-page__artifact-meta {
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-xs-size);
}
.index-run-detail-page__muted {
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
@media (max-width: 1280px) {
  .index-run-detail-page__columns {
    grid-template-columns: 1fr;
  }
}
</style>
