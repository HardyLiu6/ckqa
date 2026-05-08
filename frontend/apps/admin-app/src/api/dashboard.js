import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function fetchDashboardSummary({ courseId } = {}) {
  const params = courseId ? { courseId } : {}
  const data = unwrapApiResponse(await http.get('/dashboard/summary', { params }))
  return data
}

export async function fetchFallbackSummary({ courseId } = {}) {
  const params = courseId ? { courseId } : {}
  const [
    courses,
    materials,
    parsing,
    knowledgeBases,
    knowledgeBaseRunning,
    qaSessions,
  ] = await Promise.all([
    http.get('/courses', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    http.get('/materials', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    http.get('/material-parse-tasks', { params: { ...params, status: 'pending', pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    http.get('/knowledge-bases', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    http.get('/index-runs', { params: { ...params, status: 'running', pageSize: 100 } }).then(unwrapApiResponse).catch(() => ({})),
    http.get('/qa-sessions', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
  ])
  const runningItems = Array.isArray(knowledgeBaseRunning.items) ? knowledgeBaseRunning.items : []
  return {
    courseCount: courses.total ?? courses.summary?.total ?? null,
    materialCount: materials.total ?? null,
    materialReadyCount: materials.summary?.ready ?? null,
    materialPendingCount: parsing.total ?? null,
    knowledgeBaseCount: knowledgeBases.total ?? null,
    knowledgeBaseRunningCount: knowledgeBaseRunning.total ?? runningItems.length,
    knowledgeBaseRunningPercents: runningItems.slice(0, 2).map((it) => Math.round((it.progress || 0) * 100)),
    activeKbCount: knowledgeBases.summary?.active ?? null,
    activeKbVersion: knowledgeBases.summary?.latestVersion ?? null,
    qaSessionCount: qaSessions.total ?? null,
    qaResponseTimeMs: qaSessions.summary?.responseTimeP95Ms ?? null,
    activeKey: (knowledgeBaseRunning.total || runningItems.length) > 0 ? 'knowledgeBases' : null,
  }
}
