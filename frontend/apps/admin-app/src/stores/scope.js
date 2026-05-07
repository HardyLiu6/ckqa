import { defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { getAdminPinia } from './pinia.js'

export const SCOPE_ALL = Symbol('scope-all')
const STORAGE_KEY = 'ckqa-admin-scope'
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function resolveScopeLabel({ role, activeCourseId, courses }) {
  const safeCourses = Array.isArray(courses) ? courses : []

  if (role === 'admin') {
    return '管理员 · 全平台'
  }

  const rolePrefix = role === 'assistant' ? '助教' : '教师'

  if (activeCourseId === SCOPE_ALL) {
    return `${rolePrefix} · 全部我的课程（${safeCourses.length}）`
  }

  const matched = safeCourses.find((course) => course.id === activeCourseId)
  return `${rolePrefix} · ${matched?.name || '未知课程'}`
}

export const useScopeStore = defineStore('scope', () => {
  const state = reactive({
    activeCourseId: SCOPE_ALL,
  })

  function load() {
    if (!isBrowser) return
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (!saved) return
      const parsed = JSON.parse(saved)
      if (parsed?.activeCourseId === '__ALL__' || parsed?.activeCourseId === undefined) {
        state.activeCourseId = SCOPE_ALL
      } else if (typeof parsed.activeCourseId === 'string') {
        state.activeCourseId = parsed.activeCourseId
      }
    } catch {
      state.activeCourseId = SCOPE_ALL
    }
  }

  function save() {
    if (!isBrowser) return
    const payload = {
      activeCourseId: state.activeCourseId === SCOPE_ALL ? '__ALL__' : state.activeCourseId,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  }

  function setActiveCourseId(value) {
    state.activeCourseId = value || SCOPE_ALL
    save()
  }

  function requestParams() {
    if (state.activeCourseId === SCOPE_ALL) return {}
    return { courseId: state.activeCourseId }
  }

  return {
    state: readonly(state),
    load,
    save,
    setActiveCourseId,
    requestParams,
  }
})

export const scopeStore = useScopeStore(getAdminPinia())
