import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function getSystemHealth(client = http) {
  return unwrapApiResponse(await client.get('/system/health'))
}

export async function getSystemReadiness(client = http) {
  return unwrapApiResponse(await client.get('/system/readiness'))
}
