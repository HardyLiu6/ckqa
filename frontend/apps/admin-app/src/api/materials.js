import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function getMaterial(id, config = {}) {
  return unwrapApiResponse(await http.get(`/pdf-files/${encodeURIComponent(id)}`, config))
}

export async function listParseResults(id, config = {}) {
  return unwrapApiResponse(await http.get(`/pdf-files/${encodeURIComponent(id)}/results`, config))
}

export async function startParse(id, config = {}) {
  return unwrapApiResponse(await http.post(`/pdf-files/${encodeURIComponent(id)}/parse`, null, config))
}

export async function exportGraphRag(id, payload, config = {}) {
  return unwrapApiResponse(await http.post(`/pdf-files/${encodeURIComponent(id)}/export-graphrag`, payload, config))
}

export function hasCompleteGraphRagExport(results = [], { mode = 'section', withPageDocs = false } = {}) {
  const fileNames = new Set(results.map((item) => item?.fileName).filter(Boolean))
  const required = ['graphrag_normalized_docs.json']

  if (mode === 'page') {
    required.push('graphrag_page_docs.json')
  } else {
    required.push('graphrag_section_docs.json')

    if (withPageDocs) {
      required.push('graphrag_page_docs.json')
    }
  }

  return required.every((fileName) => fileNames.has(fileName))
}
