import { get, post } from '@/axios'

export function loginStudent(credentials) {
  return post('/auth/student/login', credentials)
}

export function registerStudent(payload) {
  return post('/auth/student/register', payload)
}

export function fetchCurrentUser() {
  return get('/auth/me')
}

export function uploadCurrentUserAvatar(file, onUploadProgress = null) {
  const formData = new FormData()
  formData.append('file', file)
  return post('/auth/me/avatar', formData, onUploadProgress ? { onUploadProgress } : {})
}
