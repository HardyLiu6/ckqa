import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listUsers(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/users', { params })))
}
