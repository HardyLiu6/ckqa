import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchCurrentUser, loginStudent, registerStudent } from '@/api/auth'
import { setAuthSessionProvider } from '@/axios'

const AUTH_STORAGE_KEY = 'ckqa-student-auth-session'

function createGuestUser() {
  return {
    id: null,
    userCode: '',
    username: '',
    name: '',
    displayName: '',
    email: '',
    role: 'guest',
    roles: [],
    permissions: [],
    dataScope: '未登录',
  }
}

function normalizeUser(profile = {}) {
  const roles = Array.isArray(profile.roles)
    ? profile.roles
    : [profile.role].filter(Boolean)
  const displayName = profile.displayName ?? profile.name ?? profile.username ?? ''

  return {
    id: profile.id ?? null,
    userCode: profile.userCode ?? '',
    username: profile.username ?? '',
    name: displayName,
    displayName,
    email: profile.email ?? '',
    role: profile.role ?? roles[0] ?? 'student',
    roles,
    permissions: Array.isArray(profile.permissions) ? profile.permissions : [],
    dataScope: profile.dataScope ?? '已加入课程',
  }
}

export const DEMO_STUDENT_ACCOUNT = {
  username: 'student.zhouzh',
  password: 'Ckqa@2026',
}

export const useUserStore = defineStore('user', () => {
  const initialSession = readStoredSession()
  const user = ref(initialSession?.user ? normalizeUser(initialSession.user) : createGuestUser())
  const token = ref(initialSession?.accessToken ?? '')
  const tokenType = ref(initialSession?.tokenType ?? 'Bearer')
  const expiresAt = ref(initialSession?.expiresAt ?? null)
  const isLoggedIn = computed(() => Boolean(token.value && user.value.id))
  const userInfo = computed(() => user.value)

  setAuthSessionProvider(() => ({
    user: user.value,
    token: token.value,
  }))

  async function login(credentials) {
    const session = await loginStudent({
      username: credentials.username?.trim(),
      password: credentials.password,
    })
    applySession(session)
    return user.value
  }

  async function register(payload) {
    const session = await registerStudent({
      username: payload.username?.trim(),
      displayName: payload.displayName?.trim(),
      password: payload.password,
    })
    applySession(session)
    return user.value
  }

  async function getUserInfo() {
    const profile = await fetchCurrentUser()
    user.value = normalizeUser(profile)
    writeStoredSession(snapshotSession())
    return user.value
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

  function setUser(userData) {
    user.value = normalizeUser({ ...user.value, ...userData })
  }

  function applySession(session) {
    user.value = normalizeUser(session.user)
    token.value = session.accessToken
    tokenType.value = session.tokenType ?? 'Bearer'
    expiresAt.value = session.expiresAt ?? null
    writeStoredSession(snapshotSession())
  }

  function snapshotSession() {
    return {
      accessToken: token.value,
      tokenType: tokenType.value,
      expiresAt: expiresAt.value,
      user: user.value,
    }
  }

  function logout() {
    user.value = createGuestUser()
    token.value = ''
    tokenType.value = 'Bearer'
    expiresAt.value = null
    clearStoredSession()
  }

  return {
    user,
    userInfo,
    token,
    tokenType,
    expiresAt,
    isLoggedIn,
    login,
    register,
    getUserInfo,
    restoreSession,
    setUser,
    logout,
  }
})

function readStoredSession() {
  try {
    const rawSession = safeLocalStorage()?.getItem(AUTH_STORAGE_KEY)
    return rawSession ? JSON.parse(rawSession) : null
  } catch {
    return null
  }
}

function writeStoredSession(session) {
  try {
    safeLocalStorage()?.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  } catch {
    // localStorage 不可用时保持内存态，不阻断登录注册流程。
  }
}

function clearStoredSession() {
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
