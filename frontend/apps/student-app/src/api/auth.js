import { get, post } from '@/axios'

// 学生端账号 + 密码登录
// 后端契约：POST /auth/student/login，{ username, password, turnstileToken? }
export function loginStudent(credentials) {
  return post('/auth/student/login', {
    username: credentials.username,
    password: credentials.password,
    turnstileToken: credentials.turnstileToken,
  })
}

// 学生端邮箱 + 验证码登录
// 后端契约：POST /auth/email/student/login，{ email, code, turnstileToken? }
export function loginWithEmailCode(payload) {
  return post('/auth/email/student/login', {
    email: payload.email,
    code: payload.code,
    turnstileToken: payload.turnstileToken,
  })
}

// 学生注册（邮箱 + 验证码可选；提供时会同步绑定并消费验证码）
export function registerStudent(payload) {
  return post('/auth/student/register', {
    username: payload.username,
    displayName: payload.displayName,
    password: payload.password,
    email: payload.email,
    emailCode: payload.emailCode,
  })
}

// 申请邮箱验证码
// scene: 'login' | 'register' | 'reset-password'
export function sendEmailVerificationCode(payload) {
  return post('/auth/email/send-code', {
    email: payload.email,
    scene: payload.scene || 'login',
    turnstileToken: payload.turnstileToken,
  })
}

// 通过邮箱验证码重置密码
export function resetPasswordByEmail(payload) {
  return post('/auth/password/reset-by-email', {
    email: payload.email,
    code: payload.code,
    newPassword: payload.newPassword,
  })
}

// 注册前检查账号 / 邮箱是否已被占用
// query: { field: 'username' | 'email', value: string }
export function checkAccountAvailability(query) {
  return get('/auth/account/availability', { params: query })
}

export function fetchCurrentUser() {
  return get('/auth/me')
}

export function uploadCurrentUserAvatar(file, onUploadProgress = null) {
  const formData = new FormData()
  formData.append('file', file)
  return post('/auth/me/avatar', formData, onUploadProgress ? { onUploadProgress } : {})
}
