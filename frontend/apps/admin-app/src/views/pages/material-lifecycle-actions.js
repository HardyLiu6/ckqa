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
    isSuccess: (results) => hasCompleteGraphRagExport(results, payload),
    isFailed: () => false,
  }
}
