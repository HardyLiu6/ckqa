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

/**
 * 个人中心：更新当前用户的显示名。
 * @param {{ displayName: string }} body
 */
export async function updateCurrentProfile(body) {
  const response = await http.put('/auth/me', body)
  return unwrapApiResponse(response)
}

/**
 * 个人中心：修改当前用户密码。
 * @param {{ oldPassword: string, newPassword: string }} body
 */
export async function changeCurrentPassword(body) {
  const response = await http.put('/auth/me/password', body)
  return unwrapApiResponse(response)
}

/**
 * 个人中心：上传头像。后端会落 MinIO 并返回新 profile。
 * @param {File|Blob} file 头像文件（image/*）
 */
export async function uploadCurrentAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post('/auth/me/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return unwrapApiResponse(response)
}
