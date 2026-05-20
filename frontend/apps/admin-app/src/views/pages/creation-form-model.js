export const ACCESS_POLICY_OPTIONS = [
  { value: 'restricted', label: '受限访问' },
  { value: 'public', label: '公开访问' },
]

export const COURSE_STATUS_OPTIONS = [
  { value: 'active', label: '启用' },
  { value: 'inactive', label: '停用' },
  { value: 'archived', label: '已归档' },
]

export const KNOWLEDGE_BASE_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿' },
  { value: 'active', label: '已启用' },
  { value: 'archived', label: '已归档' },
]

export function createCreationForm(type, options = {}) {
  if (type === 'knowledge-base') {
    return {
      courseId: options.courseOptions?.[0]?.value ?? '',
      kbCode: '',
      name: '',
      description: '',
      status: 'draft',
    }
  }

  return {
    courseName: '',
    teacherUserId: '',
    coverUrl: '',
    description: '',
    status: 'active',
    accessPolicy: 'restricted',
    category: '',
    tags: [],
    objectives: [],
    audience: [],
    difficulty: '',
    estimatedHours: null,
  }
}

export const DIFFICULTY_OPTIONS = [
  { value: '', label: '未设置' },
  { value: 'beginner', label: '入门' },
  { value: 'intermediate', label: '进阶' },
  { value: 'advanced', label: '高级' },
]

export function resolveCourseSelectOptions(courses = []) {
  return courses
    .map((course) => {
      const value = course.courseId ?? course.id
      if (!value) {
        return null
      }

      const name = course.courseName ?? course.name ?? value
      return {
        value: String(value),
        label: `${name}（${value}）`,
      }
    })
    .filter(Boolean)
}

export function resolveTeacherSelectOptions(users = []) {
  return users
    .map((user) => {
      const value = user.id ?? user.userId
      if (!value) {
        return null
      }

      const code = user.userCode ?? user.username ?? value
      const name = user.displayName ?? user.username ?? code
      return {
        value: Number(value),
        label: `${name}（${code}）`,
      }
    })
    .filter(Boolean)
}
