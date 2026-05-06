import { createPinia, defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { fetchCurrentUser, loginAdmin } from '../api/auth.js'
import { createApiError } from '../api/client.js'
import { setAuthSessionProvider } from '../axios/index.js'
import { getAdminPinia } from './pinia.js'

const AUTH_STORAGE_KEY = 'ckqa-admin-auth-session'
const LEGACY_AUTH_STORAGE_KEY = 'ckqa-admin-auth-role'

export const LOGIN_PRESETS = [
  {
    role: 'admin',
    label: '管理员',
    username: 'admin.heqh',
    password: 'Ckqa@2026',
    description: '全局配置与授权',
  },
  {
    role: 'teacher',
    label: '教师',
    username: 'teacher.zhangwb',
    password: 'Ckqa@2026',
    description: '课程资料与知识库',
  },
]

export const ROLE_PROFILES = {
  admin: {
    id: 1,
    userCode: 'ADM2026001',
    username: 'admin.heqh',
    name: '平台管理员',
    displayName: '平台管理员',
    role: 'admin',
    roles: ['admin'],
    dataScope: '全部课程',
    token: 'dev-admin-token',
    permissions: ['*'],
  },
  teacher: {
    id: 2,
    userCode: 'TCH2026001',
    username: 'teacher.zhangwb',
    name: '示例教师',
    displayName: '示例教师',
    role: 'teacher',
    roles: ['teacher'],
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
      'membership:write',
      'system:read',
    ],
  },
}

export function cloneProfile(profile = {}) {
  const roles = Array.isArray(profile.roles)
    ? profile.roles
    : [profile.role].filter(Boolean)
  const permissions = Array.isArray(profile.permissions) ? profile.permissions : []
  const displayName = profile.displayName ?? profile.name ?? profile.username ?? ''

  return {
    id: profile.id ?? null,
    userCode: profile.userCode ?? '',
    username: profile.username ?? '',
    name: displayName,
    displayName,
    role: profile.role ?? roles[0] ?? 'teacher',
    roles,
    dataScope: profile.dataScope ?? '授权课程',
    permissions: [...permissions],
  }
}

export const useAuthStore = defineStore('auth', () => {
  const initialSession = readStoredSession()
  const state = reactive({
    currentUser: initialSession?.user ? cloneProfile(initialSession.user) : null,
    token: initialSession?.accessToken ?? null,
    tokenType: initialSession?.tokenType ?? 'Bearer',
    expiresAt: initialSession?.expiresAt ?? null,
    isAuthenticated: Boolean(initialSession?.accessToken && initialSession?.user),
  })

  setAuthSessionProvider(() => ({
    currentUser: state.currentUser,
    token: state.token,
  }))

  async function login(credentials) {
    const response = await loginAdmin({
      username: credentials.username?.trim(),
      password: credentials.password,
    })
    applySession(response)
    return state.currentUser
  }

  async function loadCurrentUser() {
    if (!state.token) {
      return null
    }

    try {
      const user = await fetchCurrentUser()
      state.currentUser = cloneProfile(user)
      state.isAuthenticated = true
      writeStoredSession(snapshotSession())
      return state.currentUser
    } catch (error) {
      const apiError = createApiError(error)
      if (apiError.status === 401 || apiError.code === 4010 || apiError.code === 4011) {
        logout()
      }
      throw error
    }
  }

  function restoreSession() {
    const session = readStoredSession()
    if (!session?.accessToken || !session?.user) {
      logout()
      return false
    }
    applySession(session)
    return true
  }

  function loginAs(role) {
    const profile = ROLE_PROFILES[role]

    if (!profile) {
      throw new Error(`未知开发态身份：${role}`)
    }

    applySession({
      accessToken: profile.token,
      tokenType: 'Bearer',
      expiresAt: null,
      user: profile,
    })
  }

  function logout() {
    state.currentUser = null
    state.token = null
    state.tokenType = 'Bearer'
    state.expiresAt = null
    state.isAuthenticated = false
    clearStoredSession()
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

  function applySession(session) {
    state.currentUser = cloneProfile(session.user)
    state.token = session.accessToken
    state.tokenType = session.tokenType ?? 'Bearer'
    state.expiresAt = session.expiresAt ?? null
    state.isAuthenticated = Boolean(state.token && state.currentUser)
    writeStoredSession(snapshotSession())
  }

  function snapshotSession() {
    return {
      accessToken: state.token,
      tokenType: state.tokenType,
      expiresAt: state.expiresAt,
      user: state.currentUser,
    }
  }

  return {
    state: readonly(state),
    login,
    loginAs,
    loadCurrentUser,
    restoreSession,
    logout,
    canAccess,
  }
})

export function createAuthStore(pinia = createPinia()) {
  return useAuthStore(pinia)
}

export const authStore = useAuthStore(getAdminPinia())

function readStoredSession() {
  const storage = safeLocalStorage()
  try {
    const rawSession = storage?.getItem(AUTH_STORAGE_KEY)
    if (rawSession) {
      return JSON.parse(rawSession)
    }

    const legacyRole = storage?.getItem(LEGACY_AUTH_STORAGE_KEY)
    const profile = ROLE_PROFILES[legacyRole]
    if (profile) {
      return {
        accessToken: profile.token,
        tokenType: 'Bearer',
        expiresAt: null,
        user: profile,
      }
    }
  } catch {
    return null
  }
  return null
}

function writeStoredSession(session) {
  try {
    safeLocalStorage()?.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
    safeLocalStorage()?.removeItem(LEGACY_AUTH_STORAGE_KEY)
  } catch {
    // localStorage 不可用时保持内存态登录，不阻断联调。
  }
}

function clearStoredSession() {
  try {
    safeLocalStorage()?.removeItem(AUTH_STORAGE_KEY)
    safeLocalStorage()?.removeItem(LEGACY_AUTH_STORAGE_KEY)
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
