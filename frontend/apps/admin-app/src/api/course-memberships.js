import { normalizePageData, unwrapApiResponse } from './client.js'
import { http } from '../axios/index.js'

export async function listCourseMembers(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/course-memberships', { params })))
}

export async function createCourseMember(payload, client = http) {
  return unwrapApiResponse(await client.post('/course-memberships', payload))
}

export async function updateCourseMember(id, payload, client = http) {
  return unwrapApiResponse(await client.patch(`/course-memberships/${encodeURIComponent(id)}`, payload))
}
