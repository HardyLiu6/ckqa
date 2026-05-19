import { get } from '../axios/index.js'

export function createCoursesApi(client = { get }) {
  return {
    listCourses(params = {}) {
      return client.get('/courses', { params })
    },
    listCourseKnowledgeBases(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}/knowledge-bases`)
    },
  }
}

const coursesApi = createCoursesApi()

export const listCourses = coursesApi.listCourses
export const listCourseKnowledgeBases = coursesApi.listCourseKnowledgeBases
