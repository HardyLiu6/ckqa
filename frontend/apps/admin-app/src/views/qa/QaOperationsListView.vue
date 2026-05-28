<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
  refreshAll(1)
}

function resetFilters() {
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

    <!-- 筛选与搜索：紧凑两行布局 -->
    <section class="ops-filters" aria-label="问答运维筛选">
      <div class="filters-header">
        <div class="filters-title">
          <component :is="Filter" :size="16" />
          <span>筛选与搜索</span>
        </div>
        <span v-if="hasActiveFilters" class="filters-hint">已应用自定义筛选条件</span>
      </div>

      <!-- 第一行：关键字搜索 + 模式 + 任务状态 + 反馈 + 反馈标签 -->
      <div class="filters-row">
        <el-input
          v-model="filters.keyword"
          class="filter-search"
          clearable
          placeholder="搜索课程 / 知识库 / 学生 / 日志 ID"
          :prefix-icon="Search"
          @keyup.enter="applyFilters"
        />
        <el-select v-model="filters.mode" clearable placeholder="模式">
          <el-option label="basic" value="basic" />
          <el-option label="local" value="local" />
          <el-option label="global" value="global" />
          <el-option label="drift" value="drift" />
          <el-option label="hybrid_v0" value="hybrid_v0" />
        </el-select>
        <el-select v-model="filters.taskStatus" clearable placeholder="任务状态">
          <el-option label="成功" value="success" />
          <el-option label="进行中" value="running" />
          <el-option label="失败" value="failed" />
          <el-option label="失效" value="stale" />
        </el-select>
        <el-select v-model="filters.feedbackRating" clearable placeholder="反馈">
          <el-option label="有用" value="helpful" />
          <el-option label="无用" value="unhelpful" />
          <el-option label="待改进" value="needs_improvement" />
        </el-select>
        <el-select v-model="filters.feedbackTag" clearable placeholder="反馈标签">
          <el-option label="来源不相关" value="source_irrelevant" />
          <el-option label="太长" value="too_long" />
          <el-option label="希望举例" value="wants_example" />
          <el-option label="不清楚" value="unclear" />
          <el-option label="不正确" value="incorrect" />
        </el-select>
      </div>

      <!-- 第二行：路由置信度 + 复核优先级 + 日期区间 + 操作按钮 -->
      <div class="filters-row">
        <el-select v-model="filters.routingConfidenceBand" clearable placeholder="路由置信度">
          <el-option label="高置信" value="high_confidence" />
          <el-option label="中置信" value="medium_confidence" />
          <el-option label="低置信" value="low_confidence" />
          <el-option label="不确定" value="uncertain" />
        </el-select>
        <el-select v-model="filters.reviewPriority" clearable placeholder="复核优先级">
          <el-option label="普通" value="normal" />
          <el-option label="低置信度" value="low_confidence" />
          <el-option label="Hybrid 未就绪" value="hybrid_not_ready" />
        </el-select>
        <el-date-picker
          v-model="filters.createdFrom"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="开始日期"
        />
        <el-date-picker
          v-model="filters.createdTo"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束日期"
        />
        <div class="filters-actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            :loading="loading"
            type="primary"
            :icon="Search"
            native-type="button"
            @click="applyFilters"
          >
            筛选
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            :icon="RefreshCw"
            native-type="button"
            @click="resetFilters"
          >
            重置
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
        <el-table-column label="模式" width="120" align="center" header-align="center">
          <template #default="{ row }">
            <span
              v-if="row.queryMode"
              class="status-badge"
              :data-tone="MODE_TONE[row.queryMode] || 'blocked'"
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

      <!-- 分页 -->
      <footer class="ops-pagination">
        <span class="pagination-info">
          共 <strong>{{ pagination.total }}</strong> 条
          ·
          第 <strong>{{ pagination.page }}</strong> / {{ pagination.pages || 1 }} 页
        </span>
        <el-pagination
          background
          layout="sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          :pager-count="5"
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

/* 筛选区：紧凑两行 */
.ops-filters {
  display: grid;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}

.filters-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filters-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ckqa-text);
  font-size: 14px;
  font-weight: 600;
}

.filters-hint {
  color: var(--ckqa-accent);
  font-size: 12px;
}

.filters-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

/* 单元格基线宽度：让 flex 自动平铺，溢出再换行 */
.filters-row :deep(.el-select),
.filters-row :deep(.el-date-editor),
.filters-row :deep(.el-input):not(.filter-search) {
  flex: 1 1 160px;
  min-width: 140px;
  max-width: 220px;
}

.filter-search {
  /* 关键字搜索器更宽 */
  flex: 2 1 260px;
  min-width: 220px;
  max-width: 380px;
}

.filters-actions {
  display: flex;
  flex: 1 1 200px;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
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

/* 分页 */
.ops-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 4px;
  color: var(--ckqa-text-muted);
  flex-wrap: wrap;

  .pagination-info {
    font-size: 13px;

    strong {
      color: var(--ckqa-text);
      font-weight: 700;
    }
  }
}

/* 响应式 */
@media (max-width: 1280px) {
  .ops-overview {
    grid-template-columns: repeat(3, minmax(0, 1fr));
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
    align-items: flex-start;
  }
}

@media (max-width: 600px) {
  .ops-overview {
    grid-template-columns: 1fr;
  }

  .filters-row :deep(.el-select),
  .filters-row :deep(.el-date-editor),
  .filters-row :deep(.el-input):not(.filter-search) {
    flex-basis: 100%;
    max-width: none;
  }

  .filter-search,
  .filters-actions {
    flex-basis: 100%;
    max-width: none;
  }
}
</style>
