import {
  Check,
  Hammer,
  RefreshCw,
  WandSparkles,
} from 'lucide-vue-next'

function countDoneRows(rows) {
  return rows.filter((row) => row?.status === 'done').length
}

function normalizeItems(items) {
  return Array.isArray(items) ? items : []
}

function resolveSelectedMaterialChip(blocks) {
  const materialIds = normalizeItems(blocks?.selection?.materialIds)
  const materialCount = materialIds.length

  return {
    label: '已选资料',
    value: `${materialCount} 个`,
    tone: materialCount > 0 ? 'ok' : 'warn',
  }
}

function isIndexAvailable(indexAvailability) {
  return indexAvailability?.availability === 'available' || indexAvailability?.available === true
}

export function resolveBuildSummaryChips({ activeKey, blocks } = {}) {
  const materialItems = normalizeItems(blocks?.materials?.items)
  const materialTotal = materialItems.length
  const parseRows = normalizeItems(blocks?.parseTasks?.items)
  const doneCount = countDoneRows(parseRows)
  const exportSummary = blocks?.exportArtifacts?.summary
  const missingCount = Number(exportSummary?.missingCount ?? 0)
  const indexIsAvailable = isIndexAvailable(blocks?.indexAvailability)

  if (activeKey === 'material') {
    return [
      resolveSelectedMaterialChip(blocks),
      { label: '课程资料', value: `${materialTotal} 个`, tone: 'info' },
    ]
  }

  if (activeKey === 'parse') {
    return [
      resolveSelectedMaterialChip(blocks),
      {
        label: '解析完成',
        value: `${doneCount}/${parseRows.length}`,
        tone: doneCount === parseRows.length && parseRows.length > 0 ? 'ok' : 'info',
      },
    ]
  }

  if (activeKey === 'export') {
    return [
      { label: '已导出', value: `${exportSummary?.completeCount ?? 0} 个`, tone: 'ok' },
      { label: '缺失产物', value: `${missingCount} 个`, tone: missingCount > 0 ? 'warn' : 'ok' },
    ]
  }

  if (activeKey === 'index' || activeKey === 'qa_check') {
    return [
      { label: '可用索引', value: indexIsAvailable ? '已就绪' : '暂无', tone: indexIsAvailable ? 'ok' : 'info' },
    ]
  }

  return [resolveSelectedMaterialChip(blocks)]
}

export function resolveBuildPrimaryActionIcon(operationKey) {
  const key = typeof operationKey === 'string' ? operationKey : ''

  if (key === 'qa-smoke') return WandSparkles
  if (key.includes('refresh')) return RefreshCw
  if (key.includes('confirm')) return Check

  return Hammer
}

export function resolveBuildStepIndexLabel(steps, activeStepKey) {
  if (!Array.isArray(steps)) return '01'

  const index = steps.findIndex((step) => step?.key === activeStepKey)
  return index >= 0 ? String(index + 1).padStart(2, '0') : '01'
}
