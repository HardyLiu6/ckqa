import { createPinia, defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { getAdminPinia } from './pinia.js'

const AUTH_STORAGE_KEY = 'ckqa-admin-auth-role'

export const ROLE_PROFILES = {
  admin: {
    id: 1,
    userCode: 'ADM2026001',
    name: '平台管理员',
    role: 'admin',
    dataScope: '全部课程',
    token: 'dev-admin-token',
    permissions: ['*'],
  },
  teacher: {
    id: 2,
    userCode: 'TCH2026001',
    name: '示例教师',
    role: 'teacher',
    dataScope: '授权课程',
    token: 'dev-teacher-token',
    permissions: [
      'course:read',
      'material:read',
      'material:parse',
      'material:export',
      'kb:read',
      'kb:write',
      'kb:index',
      'kb:activate',
      'qa:read',
      'qa:log:read',
      'membership:read',
      'system:read',
    ],
  },
}

export function cloneProfile(profile) {
  return {
    id: profile.id,
    userCode: profile.userCode,
    name: profile.name,
    role: profile.role,
    dataScope: profile.dataScope,
    permissions: [...profile.permissions],
  }
}

export const useAuthStore = defineStore('auth', () => {
  const initialProfile = readStoredProfile()
  const state = reactive({
    currentUser: initialProfile ? cloneProfile(initialProfile) : null,
    token: initialProfile?.token ?? null,
    isAuthenticated: Boolean(initialProfile),
  })

  function loginAs(role) {
    const profile = ROLE_PROFILES[role]

    if (!profile) {
      throw new Error(`未知开发态身份：${role}`)
    }

    state.currentUser = cloneProfile(profile)
    state.token = profile.token
    state.isAuthenticated = true
    writeStoredRole(role)
  }

  function logout() {
    state.currentUser = null
    state.token = null
    state.isAuthenticated = false
    clearStoredRole()
  }

  function canAccess(requiredPermissions = []) {
    if (!requiredPermissions.length) {
      return true
    }

    const permissions = state.currentUser?.permissions ?? []

    if (permissions.includes('*')) {
      return true
    }

    return requiredPermissions.every((permission) => permissions.includes(permission))
  }

  return {
    state: readonly(state),
    loginAs,
    logout,
    canAccess,
  }
})

export function createAuthStore(pinia = createPinia()) {
  return useAuthStore(pinia)
}

export const authStore = useAuthStore(getAdminPinia())

function readStoredProfile() {
  const storage = safeLocalStorage()
  try {
    const role = storage?.getItem(AUTH_STORAGE_KEY)
    return ROLE_PROFILES[role] ?? null
  } catch {
    return null
  }
}

function writeStoredRole(role) {
  try {
    safeLocalStorage()?.setItem(AUTH_STORAGE_KEY, role)
  } catch {
    // localStorage 不可用时保持内存态登录，不阻断开发态联调。
  }
}

function clearStoredRole() {
  try {
    safeLocalStorage()?.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // localStorage 不可用时无需额外处理。
  }
}

function safeLocalStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null
  } catch {
    return null
  }
}
