import { reactive, readonly } from 'vue'

export const ROLE_PROFILES = {
  admin: {
    id: 1,
    name: '平台管理员',
    role: 'admin',
    dataScope: '全部课程',
    token: 'dev-admin-token',
    permissions: ['*'],
  },
  teacher: {
    id: 2,
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

function cloneProfile(profile) {
  return {
    id: profile.id,
    name: profile.name,
    role: profile.role,
    dataScope: profile.dataScope,
    permissions: [...profile.permissions],
  }
}

export function createAuthStore() {
  const state = reactive({
    currentUser: null,
    token: null,
    isAuthenticated: false,
  })

  function loginAs(role) {
    const profile = ROLE_PROFILES[role]

    if (!profile) {
      throw new Error(`未知开发态身份：${role}`)
    }

    state.currentUser = cloneProfile(profile)
    state.token = profile.token
    state.isAuthenticated = true
  }

  function logout() {
    state.currentUser = null
    state.token = null
    state.isAuthenticated = false
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
}

export const authStore = createAuthStore()
