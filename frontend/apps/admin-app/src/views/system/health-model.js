const SERVICE_LABELS = {
  javaApi: 'Java API',
  mysql: 'MySQL',
  pdfIngestRoot: 'pdf_ingest root',
  graphRagRoot: 'graphrag_pipeline root',
  'graphrag-root': 'graphrag_pipeline root',
  graphRagBuildRunsRoot: 'GraphRAG build-runs root',
  'graphrag-build-runs-root': 'GraphRAG build-runs root',
  graphRagApi: 'GraphRAG API',
  'graphrag-api': 'GraphRAG API',
  graphRagReady: 'GraphRAG ready',
  'graphrag-ready': 'GraphRAG ready',
  lanceDb: 'output/lancedb',
}

function serviceTone(service = {}) {
  if (!service.reachable) return 'danger'
  if (!service.ready) return 'warning'
  return 'success'
}

export function normalizeHealthResponse(payload = {}) {
  const services = Object.entries(payload.services || {}).map(([key, value = {}]) => {
    const path = value.path || value.endpoint || ''

    return {
      key,
      label: SERVICE_LABELS[key] || key,
      reachable: Boolean(value.reachable),
      ready: Boolean(value.ready),
      message: value.message || '',
      ...(path ? { path } : {}),
      tone: serviceTone(value),
    }
  })

  return {
    overallStatus: payload.status || 'unknown',
    checkedAt: payload.checkedAt || '',
    services,
    raw: payload,
  }
}
