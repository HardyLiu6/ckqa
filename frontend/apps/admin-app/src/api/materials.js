import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listCourseMaterialPage(courseId, params = {}, client = http) {
  const data = unwrapApiResponse(await client.get(
    `/courses/${encodeURIComponent(courseId)}/materials`,
    { params: normalizeCourseMaterialQueryParams(params) },
  ))
  return normalizePageData(data)
}

export async function uploadCourseMaterial(
  courseId,
  { file, displayName = '', materialType = 'textbook', onUploadProgress = null } = {},
  client = http,
) {
  const formData = new FormData()
  formData.append('file', file)
  if (String(displayName ?? '').trim()) {
    formData.append('displayName', String(displayName).trim())
  }
  if (String(materialType ?? '').trim()) {
    formData.append('materialType', String(materialType).trim())
  }

  return unwrapApiResponse(await client.post(
    `/courses/${encodeURIComponent(courseId)}/materials`,
    formData,
    onUploadProgress ? { onUploadProgress } : {},
  ))
}

export async function updateCourseMaterial(courseId, materialId, payload = {}, client = http) {
  return unwrapApiResponse(await client.patch(
    `/courses/${encodeURIComponent(courseId)}/materials/${encodeURIComponent(materialId)}`,
    payload,
  ))
}

export async function getCourseMaterial(courseId, materialId, client = http) {
  return unwrapApiResponse(await client.get(
    `/courses/${encodeURIComponent(courseId)}/materials/${encodeURIComponent(materialId)}`,
  ))
}

export async function deleteCourseMaterial(courseId, materialId, client = http) {
  return unwrapApiResponse(await client.delete(
    `/courses/${encodeURIComponent(courseId)}/materials/${encodeURIComponent(materialId)}`,
  ))
}

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

function normalizeCourseMaterialQueryParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}
