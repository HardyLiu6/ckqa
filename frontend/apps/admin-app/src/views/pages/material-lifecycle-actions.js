import { hasCompleteGraphRagExport } from '../../api/materials.js'

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

export function createParallelParseTaskOptions({ rows = [], startParseRequest }) {
  const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))

  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        runnableRows.map((row) => startParseRequest(row.id, { signal })),
      )

      return summarizeSettledBatch(runnableRows.length, results)
    },
    isSuccess: (summary) => Number(summary?.submitted ?? 0) > 0,
    isFailed: (summary) => Number(summary?.submitted ?? 0) === 0 && Number(summary?.failed ?? 0) > 0,
  }
}

export function createExportMissingTaskOptions({ rows = [], payload, exportGraphRagRequest }) {
  const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')

  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        missingRows.map((row) => exportGraphRagRequest(row.id, payload, { signal })),
      )

      return summarizeSettledBatch(missingRows.length, results)
    },
    isSuccess: (summary) => Number(summary?.submitted ?? 0) > 0,
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
