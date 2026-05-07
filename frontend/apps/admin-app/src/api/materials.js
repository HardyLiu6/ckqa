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

export async function fetchParseResultContent(accessUrl, config = {}, client = http) {
  const response = await client.get(normalizeParseResultAccessPath(accessUrl), {
    ...config,
    responseType: 'blob',
  })
  return {
    blob: response.data,
    fileName: resolveContentDispositionFileName(response.headers?.['content-disposition']) ?? 'parse-result',
    contentType: response.headers?.['content-type'] ?? response.data?.type ?? 'application/octet-stream',
  }
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

function normalizeParseResultAccessPath(accessUrl) {
  const raw = String(accessUrl ?? '').trim()
  if (!raw) return ''

  const path = raw.startsWith('http://') || raw.startsWith('https://')
    ? new URL(raw).pathname
    : raw

  return path.startsWith('/api/v1/')
    ? path.slice('/api/v1'.length)
    : path
}

function resolveContentDispositionFileName(value = '') {
  const match = String(value).match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i)
  const encoded = match?.[1] ?? match?.[2]
  if (!encoded) return null

  try {
    return decodeURIComponent(encoded)
  } catch {
    return encoded
  }
}

function normalizeCourseMaterialQueryParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}
