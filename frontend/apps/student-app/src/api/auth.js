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
