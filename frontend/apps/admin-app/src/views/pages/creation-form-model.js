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
  { value: 'active', label: '启用' },
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
    courseId: '',
    courseName: '',
    description: '',
    status: 'active',
    accessPolicy: 'restricted',
  }
}

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
