import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

const COURSE_PAGE_SIZE_MAX = 100

export async function listCourses(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/courses', { params: normalizeCourseQueryParams(params) }))
}

export async function createCourse(payload, client = http) {
  return unwrapApiResponse(await client.post('/courses', payload))
}

export async function updateCourse(courseId, payload, client = http) {
  return unwrapApiResponse(await client.put(`/courses/${encodeURIComponent(courseId)}`, payload))
}

export async function deleteCourse(courseId, client = http) {
  return unwrapApiResponse(await client.delete(`/courses/${encodeURIComponent(courseId)}`))
}

export async function uploadCourseCover(file, courseId = null, client = http) {
  const formData = new FormData()
  formData.append('file', file)
  const path = courseId
    ? `/courses/${encodeURIComponent(courseId)}/cover`
    : '/courses/covers'
  return unwrapApiResponse(await client.post(path, formData))
}

export async function getCourse(courseId) {
  return unwrapApiResponse(await http.get(`/courses/${encodeURIComponent(courseId)}`))
}

export async function listCourseMaterials(courseId) {
  return unwrapApiResponse(await http.get(`/courses/${encodeURIComponent(courseId)}/pdf-files`))
}

export async function listCourseKnowledgeBases(courseId) {
  return unwrapApiResponse(await http.get(`/courses/${encodeURIComponent(courseId)}/knowledge-bases`))
}

function normalizeCourseQueryParams(params = {}) {
  const cleanParams = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
  const requestedSize = Number(cleanParams.size)
  if (Number.isFinite(requestedSize) && requestedSize > COURSE_PAGE_SIZE_MAX) {
    cleanParams.size = COURSE_PAGE_SIZE_MAX
  }
  return cleanParams
}
