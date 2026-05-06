import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function loginAdmin(credentials) {
  const response = await http.post('/auth/admin/login', credentials)
  return unwrapApiResponse(response)
}

export async function fetchCurrentUser() {
  const response = await http.get('/auth/me')
  return unwrapApiResponse(response)
}
