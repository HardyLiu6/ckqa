import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  fetchCurrentUser,
  loginStudent,
  loginWithEmailCode,
  registerStudent,
  resetPasswordByEmail,
  uploadCurrentUserAvatar,
} from '@/api/auth'
import { setAuthSessionProvider } from '@/axios'

const AUTH_STORAGE_KEY = 'ckqa-student-auth-session'
const REMEMBER_PROFILE_KEY = 'ckqa-student-remember-profile'
export const DEFAULT_USER_AVATAR_URL = '/api/v1/user-avatars/default-user-avatar.svg'

// 学生端"记住我"持久化时长，默认 7 天
// 调整规则：界面勾选 → rememberDays = 7；不勾选 → rememberDays = 0（仅会话有效）
export const REMEMBER_ME_DAYS = 7
const SESSION_DAY_MS = 24 * 60 * 60 * 1000

function createGuestUser() {
  return {
    id: null,
    userCode: '',
    username: '',
    name: '',
    displayName: '',
    avatarUrl: DEFAULT_USER_AVATAR_URL,
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
    avatarUrl: profile.avatarUrl || DEFAULT_USER_AVATAR_URL,
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
  const validInitialSession = isSessionAlive(initialSession) ? initialSession : null
  const user = ref(validInitialSession?.user ? normalizeUser(validInitialSession.user) : createGuestUser())
  const token = ref(validInitialSession?.accessToken ?? '')
  const tokenType = ref(validInitialSession?.tokenType ?? 'Bearer')
  const expiresAt = ref(validInitialSession?.expiresAt ?? null)
  const rememberMe = ref(Boolean(validInitialSession?.rememberMe))
  const isLoggedIn = computed(() => Boolean(token.value && user.value.id))
  const userInfo = computed(() => user.value)

  // 如果初始会话已经过期，直接清掉残留数据
  if (initialSession && !validInitialSession) {
    clearStoredSession()
  }

  setAuthSessionProvider(() => ({
    user: user.value,
    token: token.value,
  }))

  async function login(credentials) {
    const remember = Boolean(credentials.remember)
    const session = await loginStudent({
      username: credentials.username?.trim(),
      password: credentials.password,
      remember,
      rememberDays: remember ? REMEMBER_ME_DAYS : 0,
    })
    applySession(session, { rememberMe: remember })
    persistRememberedProfile(remember, {
      identifierType: 'username',
      identifier: credentials.username?.trim() ?? '',
    })
    return user.value
  }

  async function loginByEmailCode(credentials) {
    const remember = Boolean(credentials.remember)
    const session = await loginWithEmailCode({
      email: credentials.email?.trim(),
      code: credentials.code?.trim(),
      remember,
      rememberDays: remember ? REMEMBER_ME_DAYS : 0,
    })
    applySession(session, { rememberMe: remember })
    persistRememberedProfile(remember, {
      identifierType: 'email',
      identifier: credentials.email?.trim() ?? '',
    })
    return user.value
  }

  async function register(payload) {
    const session = await registerStudent({
      username: payload.username?.trim(),
      displayName: payload.displayName?.trim(),
      email: payload.email?.trim(),
      emailCode: payload.emailCode?.trim(),
      password: payload.password,
    })
    applySession(session, { rememberMe: false })
    return user.value
  }

  async function resetPassword(payload) {
    return resetPasswordByEmail({
      email: payload.email?.trim(),
      code: payload.code?.trim(),
      newPassword: payload.newPassword,
    })
  }

  async function getUserInfo() {
    const profile = await fetchCurrentUser()
    user.value = normalizeUser(profile)
    writeStoredSession(snapshotSession())
    return user.value
  }

  async function uploadAvatar(file, onUploadProgress = null) {
    const profile = await uploadCurrentUserAvatar(file, onUploadProgress)
    user.value = normalizeUser(profile)
    writeStoredSession(snapshotSession())
    return user.value
  }

  function restoreSession() {
    const session = readStoredSession()
    if (!isSessionAlive(session)) {
      logout()
      return false
    }
    applySession(session, { rememberMe: Boolean(session.rememberMe) })
    return true
  }

  function setUser(userData) {
    user.value = normalizeUser({ ...user.value, ...userData })
  }

  function applySession(session, { rememberMe: nextRemember = false } = {}) {
    user.value = normalizeUser(session.user)
    token.value = session.accessToken
    tokenType.value = session.tokenType ?? 'Bearer'
    rememberMe.value = nextRemember
    expiresAt.value = computeExpiresAt(session.expiresAt, nextRemember)
    writeStoredSession(snapshotSession())
  }

  function snapshotSession() {
    return {
      accessToken: token.value,
      tokenType: tokenType.value,
      expiresAt: expiresAt.value,
      rememberMe: rememberMe.value,
      user: user.value,
    }
  }

  function logout() {
    user.value = createGuestUser()
    token.value = ''
    tokenType.value = 'Bearer'
    expiresAt.value = null
    rememberMe.value = false
    clearStoredSession()
  }

  return {
    user,
    userInfo,
    token,
    tokenType,
    expiresAt,
    rememberMe,
    isLoggedIn,
    login,
    loginByEmailCode,
    register,
    resetPassword,
    getUserInfo,
    uploadAvatar,
    restoreSession,
    setUser,
    logout,
  }
})

function computeExpiresAt(serverExpiresAt, rememberMe) {
  // 优先使用后端给的过期时间；若没有，则按"记住我"补一个本地兜底（默认 7 天，否则 1 天）
  if (typeof serverExpiresAt === 'number' && serverExpiresAt > 0) {
    return serverExpiresAt
  }
  if (typeof serverExpiresAt === 'string' && serverExpiresAt) {
    const parsed = Date.parse(serverExpiresAt)
    if (!Number.isNaN(parsed)) return parsed
  }
  const days = rememberMe ? REMEMBER_ME_DAYS : 1
  return Date.now() + days * SESSION_DAY_MS
}

function isSessionAlive(session) {
  if (!session?.accessToken || !session?.user) return false
  if (!session.expiresAt) return true
  return Number(session.expiresAt) > Date.now()
}

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

// 把上次登录使用的标识（账号 / 邮箱）记下来，方便回到登录页时自动回填
function persistRememberedProfile(remember, profile) {
  const storage = safeLocalStorage()
  if (!storage) return
  try {
    if (remember && profile?.identifier) {
      storage.setItem(REMEMBER_PROFILE_KEY, JSON.stringify(profile))
    } else {
      storage.removeItem(REMEMBER_PROFILE_KEY)
    }
  } catch {
    // 忽略写入异常，登录主流程不应被持久化失败阻塞
  }
}

export function readRememberedProfile() {
  try {
    const raw = safeLocalStorage()?.getItem(REMEMBER_PROFILE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function safeLocalStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null
  } catch {
    return null
  }
}
