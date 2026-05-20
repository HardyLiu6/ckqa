// 学生端课程相关 REST 接口封装
// 路径前缀对齐 docs/student-backend-graphrag-api-contract.md
// /api/v1/courses
//
// 同时支持两种用法：
//   1. 直接命名导出：`import { listCourses } from '@/api/courses'`（视图 / store 使用）
//   2. 工厂模式：`createCoursesApi(client)`（单元测试注入 mock client 使用）

import { get } from '../axios/index.js'

export function createCoursesApi(client = { get }) {
  return {
    /** 课程列表（按当前学生 / userCode 自动过滤可见范围） */
    listCourses(params = {}) {
      return client.get('/courses', { params })
    },
    /** 课程详情（含 category / tags / objectives / audience / difficulty / estimatedHours） */
    getCourseDetail(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}`)
    },
    /** 课程关联的资料列表（学生端只读，作为最小化版本"课程目录"展示） */
    listCourseMaterials(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}/pdf-files`)
    },
    /** 课程关联知识库 */
    listCourseKnowledgeBases(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}/knowledge-bases`)
    },
    /** 课程章节占位接口（二期上线前返回 featureStatus=coming-soon） */
    listCourseChapters(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}/chapters`)
    },
    /** 当前学生在该课程下的学习进度占位接口 */
    getMyCourseProgress(courseId) {
      return client.get(`/courses/${encodeURIComponent(courseId)}/progress/me`)
    },
  }
}

const coursesApi = createCoursesApi()

export const listCourses = coursesApi.listCourses
export const getCourseDetail = coursesApi.getCourseDetail
export const listCourseMaterials = coursesApi.listCourseMaterials
export const listCourseKnowledgeBases = coursesApi.listCourseKnowledgeBases
export const listCourseChapters = coursesApi.listCourseChapters
export const getMyCourseProgress = coursesApi.getMyCourseProgress
