import { hasCompleteGraphRagExport } from '../../api/materials.js'
import { resolveParseTaskRows } from './module-content.js'

export function resolveMaterialExportPayload(actions = {}, confirmOverwrite) {
  const payload = { ...(actions.exportPayload ?? { mode: 'section', withPageDocs: true, force: false }) }

  if (!actions.hasCompleteExport) {
    return payload
  }

  if (typeof confirmOverwrite !== 'function') {
    return payload
  }

  if (!confirmOverwrite('已存在完整 GraphRAG 导出结果，是否覆盖重新导出？')) {
    return null
  }

  return {
    ...payload,
    force: true,
  }
}

export function createMaterialExportTaskOptions({
  materialId,
  payload,
  exportGraphRagRequest,
  listParseResultsRequest,
}) {
  return {
    trigger: ({ signal }) => exportGraphRagRequest(materialId, payload, { signal }),
    poll: ({ signal }) => listParseResultsRequest(materialId, { signal }),
    isSuccess: (results) => Array.isArray(results) && hasCompleteGraphRagExport(results, payload),
    isFailed: () => false,
  }
}

export function createParallelParseTaskOptions({ rows = [], startParseRequest, listMaterialsRequest }) {
  const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))
  const runnableIds = new Set(runnableRows.map((row) => String(row.id)))
  const shouldPoll = typeof listMaterialsRequest === 'function'

  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        runnableRows.map((row) => startParseRequest(row.id, { signal })),
      )

      return summarizeSettledBatch(runnableRows.length, results)
    },
    poll: shouldPoll
      ? ({ signal } = {}) => listMaterialsRequest({ signal })
      : undefined,
    isSuccess: (snapshot) => {
      if (!shouldPoll) {
        return Number(snapshot?.submitted ?? 0) > 0
      }
      if (!Array.isArray(snapshot)) {
        return false
      }
      const rowsById = resolveParseTaskRows(snapshot)
        .filter((row) => runnableIds.has(String(row.id)))
      return rowsById.length === runnableIds.size
        && rowsById.every((row) => row.status === 'done')
    },
    isFailed: (summary) => Number(summary?.submitted ?? 0) === 0 && Number(summary?.failed ?? 0) > 0,
  }
}

export function createExportMissingTaskOptions({ rows = [], payload, exportGraphRagRequest, listParseResultsRequest }) {
  const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')
  const shouldPoll = typeof listParseResultsRequest === 'function'

  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        missingRows.map((row) => exportGraphRagRequest(row.id, payload, { signal })),
      )

      return summarizeSettledBatch(missingRows.length, results)
    },
    poll: shouldPoll
      ? ({ signal } = {}) => Promise.all(
          missingRows.map(async (row) => ({
            id: row.id,
            results: await listParseResultsRequest(row.id, { signal }),
          })),
        )
      : undefined,
    isSuccess: (snapshot) => {
      if (!shouldPoll) {
        return Number(snapshot?.submitted ?? 0) > 0
      }
      if (!Array.isArray(snapshot)) {
        return false
      }
      return snapshot.length === missingRows.length
        && snapshot.every((item) => hasCompleteGraphRagExport(item.results, payload))
    },
    isFailed: (summary) => Number(summary?.submitted ?? 0) === 0 && Number(summary?.failed ?? 0) > 0,
  }
}

function summarizeSettledBatch(total, results) {
  return {
    total,
    submitted: results.filter((item) => item.status === 'fulfilled').length,
    failed: results.filter((item) => item.status === 'rejected').length,
    results,
  }
}
