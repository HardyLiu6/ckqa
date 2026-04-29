import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function listCourses(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/courses', { params }))
}

export async function createCourse(payload, client = http) {
  return unwrapApiResponse(await client.post('/courses', payload))
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
