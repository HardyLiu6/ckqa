import {
  formatRelativeSessionTime,
  normalizeQaSessionPage,
} from '../qa/qa-session-model.js'

export const DEFAULT_HOME_COURSE_COVER = '/api/v1/course-covers/default-course-cover.svg'

const MOCK_PROGRESS_SEQUENCE = [76, 58, 34]

export function resolveHomeGreetingName(user = {}) {
  const name = user.displayName || user.name || user.username || ''
  return String(name).trim() || '同学'
}

export function buildHomeCourseItems(courses = []) {
  return (Array.isArray(courses) ? courses : [])
    .filter((course) => course?.id || course?.courseId)
    .map((course, index) => ({
      id: course.id ?? course.courseId,
      title: course.title || course.courseName || course.name || course.courseId || '未命名课程',
      cover: course.cover || course.coverUrl || DEFAULT_HOME_COURSE_COVER,
      progress: normalizeProgress(course.progress ?? course.learningProgress, index),
      lastLearnAt: course.lastLearnAt || formatCourseUpdatedAt(course.updatedAt),
      meta: courseMetaText(course),
    }))
}

export function buildHomeRecentQaItems(sessionPayload, courseNameById = {}, now = new Date()) {
  return normalizeQaSessionPage(sessionPayload).items.slice(0, 3).map((session, index) => {
    const referenceTime = session.lastMessageAt || session.createdAt || session.indexLockedAt || ''
    return {
      id: session.id,
      title: session.title || '未命名问答',
      time: formatRelativeSessionTime(referenceTime, now),
      subject: courseNameById[session.courseId] || session.courseId || '未绑定课程',
      active: index === 0,
      route: { path: '/qa/ask', query: { sessionId: session.id } },
    }
  })
}

function normalizeProgress(value, index) {
  const number = Number(value)
  if (Number.isFinite(number)) {
    return Math.min(100, Math.max(0, Math.round(number)))
  }
  return MOCK_PROGRESS_SEQUENCE[index % MOCK_PROGRESS_SEQUENCE.length]
}

function formatCourseUpdatedAt(value) {
  if (!value) {
    return '最近一次学习'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '最近一次学习'
  }
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `最近更新：${month}/${day}`
}

function courseMetaText(course = {}) {
  const materialCount = Number(course.materialCount ?? 0)
  const knowledgeBaseCount = Number(course.activeKnowledgeBaseCount ?? course.knowledgeBaseCount ?? 0)
  const parts = []
  if (Number.isFinite(materialCount) && materialCount > 0) {
    parts.push(`${materialCount} 份资料`)
  }
  if (Number.isFinite(knowledgeBaseCount) && knowledgeBaseCount > 0) {
    parts.push(`${knowledgeBaseCount} 个知识库`)
  }
  return parts.join(' · ') || '课程资料待完善'
}
