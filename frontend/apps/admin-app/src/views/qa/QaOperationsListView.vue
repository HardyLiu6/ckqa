<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Copy, Download, Filter, RefreshCw, Search, Eye } from 'lucide-vue-next'

import StatusBadge from '../../components/common/StatusBadge.vue'
import {
  exportQaOperationLogs,
  getQaOperationsSummary,
  listQaOperationLogs,
} from '../../api/qa-operations.js'

const router = useRouter()
const loading = ref(false)
const summaryLoading = ref(false)
const exporting = ref(false)
const errorMessage = ref('')
const rows = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0, pages: 0 })

// 后端聚合统计（按当前筛选条件全库聚合）
const summary = ref({ total: 0, success: 0, failed: 0, lowConfidence: 0, needReview: 0 })

// 筛选条件，包含关键字搜索器
const filters = reactive({
  keyword: '',
  mode: '',
  taskStatus: '',
  feedbackRating: '',
  feedbackTag: '',
  routingConfidenceBand: '',
  reviewPriority: '',
  createdFrom: '',
  createdTo: '',
})

// 路由置信度→ tone（送给 StatusBadge）
const CONFIDENCE_TONE = {
  high_confidence: 'success',
  medium_confidence: 'warning',
  low_confidence: 'danger',
  uncertain: 'blocked',
}

const CONFIDENCE_LABEL = {
  high_confidence: '高置信',
  medium_confidence: '中置信',
  low_confidence: '低置信',
  uncertain: '不确定',
}

// 模式徽章样式：使用与系统状态徽章一致的视觉语言
const MODE_TONE = {
  basic: 'blocked',
  local: 'running',
  global: 'success',
  drift: 'warning',
  hybrid_v0: 'danger',
}

// 表格行：归一化反馈摘要
const tableRows = computed(() =>
  rows.value.map((row) => ({
    ...row,
    feedbackSummaryText: formatFeedbackSummary(row.feedbackSummary),
  })),
)

/**
 * 概览卡片：统一来自后端 /qa-operations/logs/summary 聚合接口，
 * 全部基于「全库」按当前筛选条件统计，不再混用前端当前页数据。
 */
const overviewStats = computed(() => [
  { key: 'total', label: '总样本数', value: summary.value.total, tone: 'neutral' },
  { key: 'success', label: '成功任务', value: summary.value.success, tone: 'success' },
  { key: 'failed', label: '失败/失效', value: summary.value.failed, tone: 'danger' },
  { key: 'lowConfidence', label: '低置信度', value: summary.value.lowConfidence, tone: 'warning' },
  { key: 'review', label: '待复核', value: summary.value.needReview, tone: 'primary' },
])

// 是否存在已激活的筛选条件（含关键字）
const hasActiveFilters = computed(() =>
  Object.values(filters).some((value) => String(value ?? '').trim()),
)

onMounted(() => {
  refreshAll()
})

/**
 * 自动筛选：监听筛选条件变化即触发查询。
 * - 关键字使用防抖，避免每个按键都触发请求
 * - 其他选择类条件（select / date）即时生效
 * - 通过 skipNextWatch 标记跳过重置 / 初始化后的回环
 */
const FILTER_DEBOUNCE_MS = 350
let keywordTimer = null
let skipNextWatch = false

watch(
  () => ({ ...filters }),
  (next, prev) => {
    if (skipNextWatch) {
      skipNextWatch = false
      return
    }
    const keywordChanged = (next.keyword ?? '') !== (prev?.keyword ?? '')
    const otherChanged = Object.keys(next).some(
      (key) => key !== 'keyword' && next[key] !== prev?.[key],
    )
    if (keywordChanged && !otherChanged) {
      // 关键字单独变化时防抖
      clearTimeout(keywordTimer)
      keywordTimer = setTimeout(() => refreshAll(1), FILTER_DEBOUNCE_MS)
      return
    }
    if (otherChanged) {
      // 选择类条件即时生效
      clearTimeout(keywordTimer)
      refreshAll(1)
    }
  },
  { deep: true },
)

/** 刷新当前页 + 概览统计；筛选 / 重置 / 顶部刷新都走这一入口。 */
async function refreshAll(page = pagination.page) {
  await Promise.all([loadLogs(page), loadSummary()])
}

async function loadLogs(page = pagination.page) {
  loading.value = true
  errorMessage.value = ''
  try {
    const pageData = await listQaOperationLogs(buildParams(page))
    rows.value = pageData.items
    Object.assign(pagination, pageData.pagination)
  } catch (error) {
    errorMessage.value = error?.message || '问答运维数据加载失败'
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const data = await getQaOperationsSummary(buildSummaryParams())
    summary.value = {
      total: Number(data?.total ?? 0),
      success: Number(data?.success ?? 0),
      failed: Number(data?.failed ?? 0),
      lowConfidence: Number(data?.lowConfidence ?? 0),
      needReview: Number(data?.needReview ?? 0),
    }
  } catch {
    // 概览失败不打断主表加载，给一个安全的零值即可
    summary.value = { total: 0, success: 0, failed: 0, lowConfidence: 0, needReview: 0 }
  } finally {
    summaryLoading.value = false
  }
}

function buildParams(page = 1) {
  return {
    page,
    size: pagination.size,
    ...activeFilters(),
  }
}

function buildSummaryParams() {
  // 概览不需要分页参数
  return activeFilters()
}

function activeFilters() {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => String(value ?? '').trim()),
  )
}

function applyFilters() {
  // 仍保留：搜索框 enter 立即提交（绕过 350ms 防抖）
  clearTimeout(keywordTimer)
  refreshAll(1)
}

function resetFilters() {
  // 重置时一次性清空，标记跳过 watch 触发，再手动刷新一次即可
  skipNextWatch = true
  Object.assign(filters, {
    keyword: '',
    mode: '',
    taskStatus: '',
    feedbackRating: '',
    feedbackTag: '',
    routingConfidenceBand: '',
    reviewPriority: '',
    createdFrom: '',
    createdTo: '',
  })
  clearTimeout(keywordTimer)
  refreshAll(1)
}

function handlePageChange(page) {
  loadLogs(page)
}

function handlePageSizeChange(size) {
  pagination.size = size
  loadLogs(1)
}

function openDetail(row) {
  const id = row?.retrievalLogId ?? row?.id
  if (id) {
    router.push(`/app/retrieval-logs/${encodeURIComponent(id)}`)
  }
}

async function copyLogId(row) {
  const id = row?.retrievalLogId ?? row?.id
  if (id == null) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(String(id))
      ElMessage.success(`已复制日志 ID：${id}`)
    } else {
      ElMessage.info(`日志 ID：${id}`)
    }
  } catch {
    ElMessage.info(`日志 ID：${id}`)
  }
}

async function exportSamples() {
  if (hasActiveFilters.value) {
    try {
      await ElMessageBox.confirm('将按当前筛选条件导出问答样本，是否继续？', '导出确认', {
        confirmButtonText: '继续导出',
        cancelButtonText: '取消',
        type: 'info',
      })
    } catch {
      return
    }
  }
  exporting.value = true
  try {
    const samples = await exportQaOperationLogs(buildParams(1))
    const blob = new Blob([JSON.stringify(samples, null, 2)], {
      type: 'application/json;charset=utf-8',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `qa-operation-samples-${new Date().toISOString().slice(0, 10)}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${Array.isArray(samples) ? samples.length : 0} 条样本`)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.message || '样本导出失败')
    }
  } finally {
    exporting.value = false
  }
}

function formatFeedbackSummary(summaryRow = {}) {
  const helpful = Number(summaryRow.helpful ?? 0)
  const unhelpful = Number(summaryRow.unhelpful ?? 0)
  const needsImprovement = Number(summaryRow.needsImprovement ?? 0)
  const sourceIssues = Number(summaryRow.sourceIssue ?? summaryRow.sourceIssues ?? 0)
  if (helpful + unhelpful + needsImprovement + sourceIssues <= 0) {
    return '暂无反馈'
  }
  return `有用 ${helpful} / 无用 ${unhelpful} / 待改进 ${needsImprovement} / 来源问题 ${sourceIssues}`
}

function formatDuration(row = {}) {
  const value = Number(row.durationMs ?? 0)
  if (!Number.isFinite(value) || value <= 0) {
    return '-'
  }
  return value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${value}ms`
}

function routingReviewText(row = {}) {
  const priority = row.routingReviewPriority || 'normal'
  if (priority === 'low_confidence') {
    return '重点收集'
  }
  if (priority === 'hybrid_not_ready') {
    return 'Hybrid 未就绪'
  }
  return ''
}

function queryStrategyText(row = {}) {
  const strategy = row.queryEngineStrategy || 'cli'
  if (row.historyFallbackReason) {
    return `${strategy} / 已降级`
  }
  if (row.memoryApplied) {
    return `${strategy} / 记忆 ${row.memorySourceCount ?? 0}`
  }
  return strategy
}
</script>

<template>
  <section class="qa-ops-page">
    <!-- 顶部信息卡 -->
    <header class="ops-hero">
      <div class="hero-text">
        <p class="eyebrow">QA Operations</p>
        <h1>问答运维列表</h1>
        <p class="hero-desc">按任务、模式、反馈和来源质量快速定位真实问答样本，支持检索、筛选与样本导出。</p>
      </div>
      <div class="hero-actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          :loading="loading || summaryLoading"
          :icon="RefreshCw"
          native-type="button"
          @click="refreshAll(pagination.page)"
        >
          刷新
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          :loading="exporting"
          :icon="Download"
          native-type="button"
          @click="exportSamples"
        >
          导出样本
        </el-button>
      </div>
    </header>

    <!-- 概览指标：来自后端 /qa-operations/logs/summary 全库聚合 -->
    <section v-loading="summaryLoading" class="ops-overview" aria-label="问答运维概览">
      <article
        v-for="item in overviewStats"
        :key="item.key"
        class="overview-card"
        :data-tone="item.tone"
      >
        <span class="overview-label">{{ item.label }}</span>
        <strong class="overview-value">{{ item.value }}</strong>
      </article>
    </section>

    <!-- 筛选与搜索：复用系统 .table-toolbar 圆润胶囊样式 -->
    <section class="ops-filters" aria-label="问答运维筛选">
      <div class="filters-header">
        <div class="filters-title">
          <component :is="Filter" :size="16" />
          <span>筛选与搜索</span>
          <span class="filters-auto-hint">条件变更即时生效</span>
        </div>
        <span v-if="hasActiveFilters" class="filters-hint">已应用自定义筛选条件</span>
      </div>

      <div class="table-toolbar" role="group">
        <label class="table-toolbar-field table-toolbar-field--search">
          <el-input
            v-model="filters.keyword"
            class="table-search-input"
            clearable
            placeholder="搜索课程 / 知识库 / 学生 / 日志 ID"
            :prefix-icon="Search"
            @keyup.enter="applyFilters"
          />
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="info" effect="light">模式</el-tag>
          <el-select
            v-model="filters.mode"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="basic" value="basic" />
            <el-option label="local" value="local" />
            <el-option label="global" value="global" />
            <el-option label="drift" value="drift" />
            <el-option label="hybrid_v0" value="hybrid_v0" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="primary" effect="light">状态</el-tag>
          <el-select
            v-model="filters.taskStatus"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="成功" value="success" />
            <el-option label="进行中" value="running" />
            <el-option label="失败" value="failed" />
            <el-option label="失效" value="stale" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="success" effect="light">反馈</el-tag>
          <el-select
            v-model="filters.feedbackRating"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="有用" value="helpful" />
            <el-option label="无用" value="unhelpful" />
            <el-option label="待改进" value="needs_improvement" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="warning" effect="light">标签</el-tag>
          <el-select
            v-model="filters.feedbackTag"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="来源不相关" value="source_irrelevant" />
            <el-option label="太长" value="too_long" />
            <el-option label="希望举例" value="wants_example" />
            <el-option label="不清楚" value="unclear" />
            <el-option label="不正确" value="incorrect" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="success" effect="light">置信度</el-tag>
          <el-select
            v-model="filters.routingConfidenceBand"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="高置信" value="high_confidence" />
            <el-option label="中置信" value="medium_confidence" />
            <el-option label="低置信" value="low_confidence" />
            <el-option label="不确定" value="uncertain" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--filter">
          <el-tag class="table-toolbar-tag" type="danger" effect="light">复核</el-tag>
          <el-select
            v-model="filters.reviewPriority"
            class="table-filter-select"
            clearable
            placeholder="全部"
          >
            <el-option label="全部" value="" />
            <el-option label="普通" value="normal" />
            <el-option label="低置信度" value="low_confidence" />
            <el-option label="Hybrid 未就绪" value="hybrid_not_ready" />
          </el-select>
        </label>

        <label class="table-toolbar-field table-toolbar-field--date">
          <el-date-picker
            v-model="filters.createdFrom"
            class="table-filter-date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
          />
        </label>

        <label class="table-toolbar-field table-toolbar-field--date">
          <el-date-picker
            v-model="filters.createdTo"
            class="table-filter-date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
          />
        </label>

        <div class="table-toolbar-actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            :loading="loading || summaryLoading"
            :icon="RefreshCw"
            native-type="button"
            @click="resetFilters"
          >
            重置筛选
          </el-button>
        </div>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      type="error"
      :title="errorMessage"
      show-icon
      class="ops-alert"
    />

    <!-- 数据表格 -->
    <section class="ops-table-card">
      <el-table
        v-loading="loading"
        :data="tableRows"
        class="ckqa-el-table ops-table"
        :fit="false"
        empty-text="暂无问答样本"
      >
        <el-table-column
          prop="retrievalLogId"
          label="日志 ID"
          width="110"
          align="center"
          header-align="center"
          class-name="col-nowrap"
        >
          <template #default="{ row }">
            <span class="mono">#{{ row.retrievalLogId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="课程 / 知识库" min-width="220">
          <template #default="{ row }">
            <div class="ckqa-table-identity">
              <div class="ckqa-table-identity__copy">
                <strong class="ckqa-table-primary">{{ row.courseName || row.courseId || '-' }}</strong>
                <small class="ckqa-table-secondary">
                  {{ row.knowledgeBaseName || `KB ${row.knowledgeBaseId || '-'}` }}
                </small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学生" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.displayName || row.username || row.userCode || row.userId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="模式" width="140" align="center" header-align="center" class-name="col-nowrap">
          <template #default="{ row }">
            <span
              v-if="row.queryMode"
              class="status-badge mode-badge"
              :data-tone="MODE_TONE[row.queryMode] || 'blocked'"
              :title="row.queryMode"
            >
              {{ row.queryMode }}
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="查询策略" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ queryStrategyText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center" header-align="center">
          <template #default="{ row }">
            <StatusBadge v-if="row.taskStatus" :status="row.taskStatus" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="路由置信度" min-width="180">
          <template #default="{ row }">
            <div class="confidence-cell">
              <span
                class="status-badge"
                :data-tone="CONFIDENCE_TONE[row.routingConfidenceBand] || 'blocked'"
              >
                {{ CONFIDENCE_LABEL[row.routingConfidenceBand] || row.routingConfidenceBand || '未记录' }}
              </span>
              <span v-if="routingReviewText(row)" class="muted small">
                {{ routingReviewText(row) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="right" header-align="right">
          <template #default="{ row }">
            <span class="mono">{{ formatDuration(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceCount" label="来源" width="80" align="center" header-align="center" />
        <el-table-column prop="feedbackSummaryText" label="反馈摘要" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: row.feedbackSummaryText === '暂无反馈' }">
              {{ row.feedbackSummaryText }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" class-name="col-nowrap">
          <template #default="{ row }">
            <span class="muted small">{{ row.createdAt || '-' }}</span>
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column
          label="操作"
          width="240"
          fixed="right"
          align="center"
          header-align="center"
          class-name="ckqa-el-table__action-column"
          header-class-name="ckqa-el-table__action-column"
        >
          <template #default="{ row }">
            <div class="data-table__actions">
              <el-button
                class="ckqa-el-button ckqa-el-button--primary table-action-button"
                type="primary"
                native-type="button"
                @click.stop="openDetail(row)"
              >
                <component :is="Eye" class="button-icon" :size="15" aria-hidden="true" />
                详情
              </el-button>
              <el-button
                class="ckqa-el-button ckqa-el-button--secondary table-action-button"
                native-type="button"
                @click.stop="copyLogId(row)"
              >
                <component :is="Copy" class="button-icon" :size="15" aria-hidden="true" />
                复制 ID
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页：居中显示，左下角附"共 x 条"信息 -->
      <footer class="ops-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          :pager-count="7"
          :disabled="loading"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </footer>
    </section>
  </section>
</template>

<style scoped lang="scss">
.qa-ops-page {
  display: grid;
  gap: 18px;
  padding: 24px;
  background: var(--ckqa-bg);
  min-height: 100%;
}

/* 顶部 hero */
.ops-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-lg);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--ckqa-accent) 8%, transparent) 0%, transparent 60%),
    var(--ckqa-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);

  .hero-text {
    display: grid;
    gap: 6px;
  }

  h1 {
    margin: 0;
    color: var(--ckqa-text);
    font-size: 24px;
    font-weight: 700;
    letter-spacing: -0.01em;
  }

  .hero-desc {
    margin: 0;
    color: var(--ckqa-text-muted);
    font-size: 14px;
    line-height: 1.6;
  }
}

.eyebrow {
  margin: 0;
  color: var(--ckqa-accent) !important;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 概览统计卡片 */
.ops-overview {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  position: relative;
  display: grid;
  gap: 6px;
  padding: 14px 18px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 12px;
    bottom: 12px;
    width: 3px;
    border-radius: 2px;
    background: var(--ckqa-text-weak);
  }

  &[data-tone='success']::before { background: var(--ckqa-success); }
  &[data-tone='danger']::before { background: var(--ckqa-danger); }
  &[data-tone='warning']::before { background: var(--ckqa-warning); }
  &[data-tone='primary']::before { background: var(--ckqa-running); }

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 18px rgb(15 23 42 / 6%);
  }

  .overview-label {
    color: var(--ckqa-text-muted);
    font-size: 12px;
    font-weight: 500;
  }

  .overview-value {
    color: var(--ckqa-text);
    font-size: 22px;
    font-weight: 700;
    font-feature-settings: 'tnum';
  }
}

/* 筛选区：复用系统 .table-toolbar 圆润胶囊样式，仅做布局微调 */
.ops-filters {
  display: grid;
  gap: 10px;
}

.filters-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-left: 4px;
}

.filters-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ckqa-text);
  font-size: 14px;
  font-weight: 600;
}

.filters-auto-hint {
  margin-left: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--ckqa-accent) 12%, transparent);
  color: var(--ckqa-accent);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.filters-hint {
  color: var(--ckqa-accent);
  font-size: 12px;
}

/* 在 ops-filters 内部覆盖 toolbar 为 12 列网格，让两行整齐对齐 */
.ops-filters :deep(.table-toolbar) {
  display: grid;
  /* 12 列基础网格；搜索 4 + 模式 2 + 状态 2 + 反馈 2 + 标签 2 = 12（第一行）
     第二行：置信度 2 + 复核 2 + 日期 2×2 + 操作 4 = 12 */
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
  align-items: center;
}

.ops-filters :deep(.table-toolbar-field--search) {
  grid-column: span 4;
  flex: initial;
  max-width: none;
}

.ops-filters :deep(.table-toolbar-field--filter) {
  grid-column: span 2;
  flex: initial;
  max-width: none;
}

.ops-filters :deep(.table-toolbar-field--date) {
  grid-column: span 2;
  flex: initial;
  max-width: none;
}

.ops-filters :deep(.table-toolbar-field--filter .table-filter-select),
.ops-filters :deep(.table-toolbar-field--date .table-filter-date) {
  width: 100%;
  min-width: 0;
}

.ops-filters :deep(.table-filter-date.el-input .el-input__wrapper) {
  border-radius: var(--ckqa-radius-full);
  padding: 2px var(--ckqa-space-3);
  background: var(--ckqa-surface);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--ckqa-border) 80%, transparent);
}

.ops-filters :deep(.table-filter-date.el-input .el-input__wrapper:hover) {
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--ckqa-accent) 32%, var(--ckqa-border)),
    0 2px 6px color-mix(in srgb, var(--ckqa-accent) 8%, transparent);
}

.ops-filters :deep(.table-filter-date.el-input.is-focus .el-input__wrapper) {
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--ckqa-accent) 50%, transparent),
    0 4px 10px color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
}

.table-toolbar-actions {
  grid-column: span 4;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--ckqa-space-2);
}

.ops-alert {
  border-radius: var(--ckqa-radius-md);
}

/* 表格卡片 */
.ops-table-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.ops-table {
  border-radius: var(--ckqa-radius-sm);
  overflow: hidden;

  /* 表头单元格强制不换行 */
  :deep(th .cell) {
    white-space: nowrap;
  }

  /* col-nowrap 列：表头与单元格都不折行 */
  :deep(.col-nowrap .cell) {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 操作列：去掉默认左右内边距，让两个按钮居中且不被夹挤 */
  :deep(.ckqa-el-table__action-column .cell) {
    padding: 0 var(--ckqa-space-2);
  }
}

.muted {
  color: var(--ckqa-text-muted);

  &.small {
    font-size: 12px;
  }
}

.mono {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-feature-settings: 'tnum';
  color: var(--ckqa-text);
}

.confidence-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 模式徽章：等宽字体，最大宽度收敛，过长自动收缩 */
.mode-badge {
  max-width: 100%;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 分页：居中显示 */
.ops-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0 4px;
  color: var(--ckqa-text-muted);
  flex-wrap: wrap;
  gap: 12px;

  :deep(.el-pagination) {
    --el-pagination-bg-color: var(--ckqa-surface-muted);
    --el-pagination-button-bg-color: var(--ckqa-surface-muted);
    --el-pagination-hover-color: var(--ckqa-accent);
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 8px;
  }
}

/* 响应式 */
@media (max-width: 1280px) {
  .ops-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  /* 中等屏幕：12 → 6 列网格，3 行布局更紧凑 */
  .ops-filters :deep(.table-toolbar) {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .ops-filters :deep(.table-toolbar-field--search) {
    grid-column: span 6;
  }

  .ops-filters :deep(.table-toolbar-field--filter),
  .ops-filters :deep(.table-toolbar-field--date) {
    grid-column: span 2;
  }

  .table-toolbar-actions {
    grid-column: span 6;
    justify-content: flex-end;
  }
}

@media (max-width: 960px) {
  .ops-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .ops-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ops-pagination {
    flex-direction: column;
    align-items: center;
  }
}

@media (max-width: 600px) {
  .ops-overview {
    grid-template-columns: 1fr;
  }

  /* 小屏：每个字段单独占一行 */
  .ops-filters :deep(.table-toolbar) {
    grid-template-columns: 1fr;
  }

  .ops-filters :deep(.table-toolbar-field--search),
  .ops-filters :deep(.table-toolbar-field--filter),
  .ops-filters :deep(.table-toolbar-field--date) {
    grid-column: span 1;
  }

  .table-toolbar-actions {
    grid-column: span 1;
    justify-content: flex-end;
  }
}
</style>
