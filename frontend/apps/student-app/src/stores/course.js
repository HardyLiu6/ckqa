// 学生端课程 store（PR 1 接入真实接口）
//
// 替换思路：
//   - allCourses / myCourses 改为接口加载，原有的 mock 字段（lessonCount / studentCount /
//     rating / price 等）保留为可选字段，无值时由视图按 v-if 隐藏
//   - id 仍支持 number / string，但实际由后端 courseId（字符串）主导
//   - isEnrolled / getCourseById / enrollCourse / updateProgress 等 API 保留兼容
//     旧视图调用，但 enrollCourse / updateProgress 暂仅本地状态（功能未开放）

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

import {
  listCourses as fetchCoursesApi,
  getCourseDetail as fetchCourseDetailApi,
} from '@/api/courses'

const COURSE_PAGE_SIZE = 50

/**
 * 把后端 CourseSummaryResponse / CourseDetailResponse 适配成视图字段。
 * 视图当前 mock 期望的字段名沿用：title / cover / lessonCount / studentCount / rating / price，
 * 没有的字段保持 undefined，视图层用 v-if 判定。
 */
function adaptCourse(raw) {
  if (!raw) return null
  return {
    // 业务关键字段
    id: raw.courseId ?? raw.id, // courseId 是稳定字符串主键
    courseId: raw.courseId,
    numericId: raw.id,
    title: raw.courseName ?? '',
    description: raw.description ?? '',
    cover: raw.coverUrl ?? '',
    status: raw.status,
    accessPolicy: raw.accessPolicy,
    memberStatus: raw.memberStatus, // member / public_visitor / null
    // 元数据（PR 1 新增字段）
    category: raw.category ?? '',
    tags: Array.isArray(raw.tags) ? raw.tags : [],
    objectives: Array.isArray(raw.objectives) ? raw.objectives : [],
    audience: Array.isArray(raw.audience) ? raw.audience : [],
    difficulty: raw.difficulty,
    estimatedHours: raw.estimatedHours,
    // 教师与统计
    teacher: pickPrimaryTeacher(raw.teachers),
    teachers: Array.isArray(raw.teachers) ? raw.teachers : [],
    materialCount: raw.materialCount ?? 0,
    knowledgeBaseCount: raw.knowledgeBaseCount ?? 0,
    activeKnowledgeBaseCount: raw.activeKnowledgeBaseCount ?? 0,
    updatedAt: raw.updatedAt,
    // 二期占位字段（视图按 v-if 隐藏）
    lessonCount: undefined,
    studentCount: undefined,
    rating: undefined,
    price: undefined,
  }
}

function pickPrimaryTeacher(teachers) {
  if (!Array.isArray(teachers) || teachers.length === 0) {
    return null
  }
  const primary = teachers[0] ?? {}
  return {
    id: primary.userId ?? null,
    name: primary.displayName ?? primary.username ?? '',
    title: '', // 二期教师档案补 title / avatar / bio
    avatar: '',
  }
}

export const useCourseStore = defineStore('course', () => {
  const allCourses = ref([])
  const isLoading = ref(false)
  const errorMessage = ref('')
  const lastLoadedAt = ref(null)

  const myCourses = computed(() =>
    allCourses.value.filter((c) => c.memberStatus === 'member'),
  )

  const enrolledCourseIds = computed(() => myCourses.value.map((c) => c.id))

  const myCoursesWithDetail = computed(() => myCourses.value)

  const allCategories = computed(() => {
    const categories = new Set()
    allCourses.value.forEach((c) => {
      if (c.category) categories.add(c.category)
    })
    return Array.from(categories)
  })

  /**
   * 从后端拉课程列表（学生当前可见 = 已加入 + 公开课程）。
   *
   * @param {{ keyword?: string, status?: string, force?: boolean }} options
   */
  async function loadCourses({ keyword, status, force = false } = {}) {
    if (!force && isLoading.value) return
    isLoading.value = true
    errorMessage.value = ''
    try {
      const params = { size: COURSE_PAGE_SIZE }
      if (keyword) params.keyword = keyword
      if (status) params.status = status
      const data = await fetchCoursesApi(params)
      const items = Array.isArray(data?.items) ? data.items : []
      allCourses.value = items.map(adaptCourse).filter(Boolean)
      lastLoadedAt.value = Date.now()
    } catch (err) {
      errorMessage.value = err?.message || '加载课程失败'
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 强制按 courseId 拉单门课程详情（用于课程详情页脱 mock）。
   */
  async function loadCourseDetail(courseId) {
    if (!courseId) return null
    try {
      const data = await fetchCourseDetailApi(courseId)
      return adaptCourse(data)
    } catch (err) {
      errorMessage.value = err?.message || '加载课程详情失败'
      return null
    }
  }

  function getCourseById(id) {
    if (id === null || id === undefined) return null
    const target = String(id)
    return (
      allCourses.value.find((c) => String(c.id) === target) ??
      allCourses.value.find((c) => String(c.numericId) === target) ??
      null
    )
  }

  function isEnrolled(courseId) {
    if (!courseId) return false
    const course = getCourseById(courseId)
    return course?.memberStatus === 'member'
  }

  // 兼容旧调用：enrollCourse / updateProgress 在二期才会真正落库
  function enrollCourse() {
    /* feature: coming-soon */
  }

  function updateProgress() {
    /* feature: coming-soon */
  }

  return {
    allCourses,
    myCourses,
    enrolledCourseIds,
    myCoursesWithDetail,
    allCategories,
    isLoading,
    errorMessage,
    lastLoadedAt,
    loadCourses,
    loadCourseDetail,
    getCourseById,
    isEnrolled,
    enrollCourse,
    updateProgress,
  }
})
