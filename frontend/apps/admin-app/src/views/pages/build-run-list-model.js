/**
 * 构建历史列表页用的纯函数辅助。
 *
 * 提取出来便于在 node:test 下单测，loader 与 ModulePage 复用同一份逻辑。
 */

export const STATUS_LABELS = {
  pending: '待开始',
  running: '运行中',
  success: '已完成',
  failed: '失败',
  interrupted: '已中断',
  archived: '已归档',
}

export const STAGE_LABELS = {
  material_selection: '资料选择',
  parse: '解析检查',
  graph_input_export: '图谱输入',
  prompt: '提示词',
  index: '索引构建',
  qa_smoke: 'QA 冒烟',
  done: '已完成',
}

export const QA_STATUS_LABELS = {
  pending: '待执行',
  running: '运行中',
  success: '通过',
  failed: '失败',
  skipped: '已跳过',
}

export const BUILD_RUN_COLUMNS = [
  '构建版本',
  '状态',
  '当前阶段',
  'QA 状态',
  '激活索引',
  '创建时间',
  '更新时间',
]

/**
 * 把路由 query 转成 listKnowledgeBaseBuildRuns 的 params。
 * page 解析失败、负数都回退为 1；size 始终 20；status 默认空字符串（后端不过滤）。
 */
export function buildBuildRunListParams(query = {}) {
  const rawPage = Number(query.page)
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) : 1
  const status = String(query.status ?? '').trim()
  return { page, size: 20, status }
}

export function formatBuildVersion(value) {
  if (value == null) return '-'
  const trimmed = String(value).trim()
  return trimmed.length > 0 ? trimmed : '-'
}

/**
 * 把后端 BuildRunSummaryResponse 映射成表格行结构。
 * cells 长度必须与 BUILD_RUN_COLUMNS 一致（7 列）。
 */
export function mapBuildRunRow(knowledgeBaseId, buildRun = {}) {
  const id = buildRun.id
  const status = String(buildRun.status ?? '').trim() || 'unknown'
  const stage = String(buildRun.currentStage ?? '').trim()
  const qaStatus = String(buildRun.qaStatus ?? '').trim() || 'skipped'

  const actions = []
  if (id) {
    actions.push({
      label: '打开向导',
      to: `/app/knowledge-bases/${knowledgeBaseId}/build?buildRunId=${id}`,
    })
    actions.push({
      label: '删除',
      key: 'delete-build-run',
      icon: 'delete',
      variant: 'danger',
    })
  }

  return {
    id,
    raw: buildRun,
    cells: [
      formatBuildVersion(buildRun.buildVersion),
      {
        kind: 'status',
        status,
        label: STATUS_LABELS[status] ?? status,
        filterValue: status,
      },
      STAGE_LABELS[stage] ?? stage ?? '-',
      {
        kind: 'status',
        status: qaStatus,
        label: QA_STATUS_LABELS[qaStatus] ?? qaStatus,
        filterValue: qaStatus,
      },
      buildRun.activeIndexRunId ? `#${buildRun.activeIndexRunId}` : '-',
      buildRun.createdAt ?? '-',
      buildRun.updatedAt ?? '-',
    ],
    actions,
  }
}
