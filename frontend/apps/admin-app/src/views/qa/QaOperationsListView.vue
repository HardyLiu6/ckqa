<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { exportQaOperationLogs, listQaOperationLogs } from '../../api/qa-operations.js'

const router = useRouter()
const loading = ref(false)
const exporting = ref(false)
const errorMessage = ref('')
const rows = ref([])
const pagination = reactive({ page: 1, size: 20, total: 0, pages: 0 })
const filters = reactive({
  mode: '',
  taskStatus: '',
  feedbackRating: '',
  feedbackTag: '',
  createdFrom: '',
  createdTo: '',
})

const tableRows = computed(() => rows.value.map((row) => ({
  ...row,
  feedbackSummaryText: formatFeedbackSummary(row.feedbackSummary),
})))

onMounted(() => {
  loadLogs()
})

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

function buildParams(page = 1) {
  return {
    page,
    size: pagination.size,
    ...Object.fromEntries(
      Object.entries(filters).filter(([, value]) => String(value ?? '').trim()),
    ),
  }
}

function resetFilters() {
  Object.assign(filters, {
    mode: '',
    taskStatus: '',
    feedbackRating: '',
    feedbackTag: '',
    createdFrom: '',
    createdTo: '',
  })
  loadLogs(1)
}

function openDetail(row) {
  const id = row?.retrievalLogId ?? row?.id
  if (id) {
    router.push(`/app/retrieval-logs/${encodeURIComponent(id)}`)
  }
}

async function exportSamples() {
  exporting.value = true
  try {
    const samples = await exportQaOperationLogs(buildParams(1))
    const blob = new Blob([JSON.stringify(samples, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `qa-operation-samples-${new Date().toISOString().slice(0, 10)}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${Array.isArray(samples) ? samples.length : 0} 条样本`)
  } catch (error) {
    ElMessage.error(error?.message || '样本导出失败')
  } finally {
    exporting.value = false
  }
}

function formatFeedbackSummary(summary = {}) {
  const helpful = Number(summary.helpful ?? 0)
  const unhelpful = Number(summary.unhelpful ?? 0)
  const needsImprovement = Number(summary.needsImprovement ?? 0)
  const sourceIssues = Number(summary.sourceIssue ?? summary.sourceIssues ?? 0)
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
</script>

<template>
  <section class="qa-ops-page">
    <header class="ops-header">
      <div>
        <p class="eyebrow">QA Operations</p>
        <h1>问答运维列表</h1>
        <p>按任务、模式、反馈和来源质量快速定位真实问答样本。</p>
      </div>
      <el-button type="primary" :loading="exporting" @click="exportSamples">导出样本</el-button>
    </header>

    <section class="ops-filters" aria-label="问答运维筛选">
      <el-select v-model="filters.mode" clearable placeholder="模式">
        <el-option label="basic" value="basic" />
        <el-option label="local" value="local" />
        <el-option label="global" value="global" />
        <el-option label="drift" value="drift" />
        <el-option label="hybrid_v0" value="hybrid_v0" />
      </el-select>
      <el-select v-model="filters.taskStatus" clearable placeholder="任务状态">
        <el-option label="success" value="success" />
        <el-option label="running" value="running" />
        <el-option label="failed" value="failed" />
        <el-option label="stale" value="stale" />
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
      <el-date-picker v-model="filters.createdFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" />
      <el-date-picker v-model="filters.createdTo" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" />
      <el-button :loading="loading" type="primary" @click="loadLogs(1)">筛选</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </section>

    <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon />

    <el-table v-loading="loading" :data="tableRows" class="ops-table" @row-click="openDetail">
      <el-table-column prop="retrievalLogId" label="Log" width="86" />
      <el-table-column label="课程 / 知识库" min-width="220">
        <template #default="{ row }">
          <strong>{{ row.courseName || row.courseId || '-' }}</strong>
          <div class="muted">{{ row.knowledgeBaseName || `KB ${row.knowledgeBaseId || '-'}` }}</div>
        </template>
      </el-table-column>
      <el-table-column label="学生" min-width="150">
        <template #default="{ row }">
          <span>{{ row.displayName || row.username || row.userCode || row.userId || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="queryMode" label="模式" width="120" />
      <el-table-column prop="taskStatus" label="状态" width="110" />
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ formatDuration(row) }}</template>
      </el-table-column>
      <el-table-column prop="sourceCount" label="来源" width="90" />
      <el-table-column prop="feedbackSummaryText" label="反馈摘要" min-width="260" />
      <el-table-column prop="createdAt" label="创建时间" min-width="170" />
    </el-table>

    <footer class="ops-pagination">
      <span>共 {{ pagination.total }} 条</span>
      <el-pagination
        layout="prev, pager, next"
        :current-page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        @current-change="loadLogs"
      />
    </footer>
  </section>
</template>

<style scoped lang="scss">
.qa-ops-page {
  display: grid;
  gap: 16px;
  padding: 24px;
}

.ops-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  h1 {
    margin: 4px 0;
    color: #0f172a;
    font-size: 26px;
  }

  p {
    margin: 0;
    color: #64748b;
  }
}

.eyebrow {
  color: #2563eb !important;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.ops-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr)) repeat(4, max-content);
  gap: 10px;
  align-items: center;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.ops-table {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.muted {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.ops-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
}

@media (max-width: 960px) {
  .ops-header,
  .ops-pagination {
    align-items: flex-start;
    flex-direction: column;
  }

  .ops-filters {
    grid-template-columns: 1fr;
  }
}
</style>
